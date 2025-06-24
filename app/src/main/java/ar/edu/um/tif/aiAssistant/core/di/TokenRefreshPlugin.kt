package ar.edu.um.tif.aiAssistant.core.di

import ar.edu.um.tif.aiAssistant.core.auth.AuthManager
import ar.edu.um.tif.aiAssistant.core.customException.UnauthorizedAccessException
import ar.edu.um.tif.aiAssistant.core.data.repository.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.util.AttributeKey
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ktor HTTP client plugin that handles token refresh when receiving 401 Unauthorized responses.
 * It will automatically:
 * 1. Intercept 401 responses
 * 2. Try to refresh the token
 * 3. Retry the original request with the new token
 * 4. If refresh fails, it will throw an UnauthorizedAccessException
 */
class TokenRefreshPlugin private constructor(
    internal val authRepository: AuthRepository,
    internal val authManager: AuthManager
) {
    companion object Plugin : HttpClientPlugin<TokenRefreshConfig, TokenRefreshPlugin> {
        override val key = AttributeKey<TokenRefreshPlugin>("TokenRefreshPlugin")

        // Flag to prevent infinite refresh loops
        private val isRefreshing = AtomicBoolean(false)

        override fun prepare(block: TokenRefreshConfig.() -> Unit): TokenRefreshPlugin {
            val config = TokenRefreshConfig().apply(block)
            return TokenRefreshPlugin(config.authRepository, config.authManager)
        }

        override fun install(plugin: TokenRefreshPlugin, scope: HttpClient) {
            // Install the plugin to intercept responses
            scope.plugin(HttpSend).intercept { request ->
                // Add auth token to request if not present
                runBlocking {
                    val token = plugin.authRepository.getAccessToken()
                    if (token != null && !request.headers.contains("Authorization")) {
                        request.header("Authorization", "Bearer $token")
                    }
                }

                // Send the initial request
                val originalResponse = execute(request)

                // Check if the response is 401 Unauthorized
                if (originalResponse.response.status == HttpStatusCode.Unauthorized && !isRefreshing.get()) {
                    try {
                        // Set refreshing flag to prevent infinite loops
                        isRefreshing.set(true)

                        // Try to refresh the token using AuthManager
                        val refreshSuccessful = runBlocking {
                            plugin.authManager.verifyAuthentication()
                        }

                        if (refreshSuccessful) {
                            // Token refreshed successfully, retry the original request with new token
                            val newToken = runBlocking { plugin.authRepository.getAccessToken() }
                            val newRequest = HttpRequestBuilder().apply {
                                takeFrom(request)
                                headers.remove("Authorization")
                                header("Authorization", "Bearer $newToken")
                            }

                            // Execute the request again with the new token
                            execute(newRequest)
                        } else {
                            // If refresh failed, return the original 401 response
                            originalResponse
                        }
                    } catch (e: Exception) {
                        // Log the error and return the original response
                        android.util.Log.e("TokenRefreshPlugin", "Error refreshing token", e)
                        originalResponse
                    } finally {
                        // Reset the refreshing flag
                        isRefreshing.set(false)
                    }
                } else {
                    // For non-401 responses, just return the original response
                    originalResponse
                }
            }
        }
    }
}

/**
 * Configuration class for the TokenRefreshPlugin
 */
class TokenRefreshConfig {
    lateinit var authRepository: AuthRepository
    lateinit var authManager: AuthManager
}
