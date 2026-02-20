package ar.edu.um.tif.aiAssistant.core.di

import android.content.Context
import ar.edu.um.tif.aiAssistant.BuildConfig
import ar.edu.um.tif.aiAssistant.core.auth.AuthManager
import ar.edu.um.tif.aiAssistant.core.client.AuthApiClient
import ar.edu.um.tif.aiAssistant.core.client.AssistantApiClient
import ar.edu.um.tif.aiAssistant.core.customException.UnauthorizedAccessException
import ar.edu.um.tif.aiAssistant.core.data.repository.AuthRepository
import ar.edu.um.tif.aiAssistant.core.service.ContactService
import ar.edu.um.tif.aiAssistant.core.service.PatchResponseCoordinator
import ar.edu.um.tif.aiAssistant.core.service.SmsPermissionRequestCoordinator
import ar.edu.um.tif.aiAssistant.core.skills.CallContactSkill
import ar.edu.um.tif.aiAssistant.core.skills.SendMessageSkill
import com.justai.aimybox.core.CustomSkill
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.header
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Qualifier
import javax.inject.Singleton
import javax.inject.Provider

// Custom qualifiers to distinguish between the two HttpClient instances
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthenticatedHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
        // Enable Kotlin reflection for serialization
        classDiscriminator = "type"
        serializersModule = SerializersModule {
            // Add any custom serializers if needed
        }
    }

    /**
     * Provides a basic HttpClient without auth features.
     * This breaks the dependency cycle by not depending on AuthRepository or AuthManager.
     */
    @Provides
    @Singleton
    @BaseHttpClient
    fun provideBaseHttpClient(json: Json): HttpClient {
        return HttpClient(OkHttp) {
            // Engine configuration
            engine {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                })
            }

            // Install plugins
            install(ContentNegotiation) {
                json(json)
            }

            // Set default headers for all requests
            install(DefaultRequest) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }

            // Log HTTP requests and responses
            install(Logging) {
                logger = Logger.DEFAULT
                level = if (BuildConfig.DEBUG) {
                    LogLevel.ALL
                } else {
                    LogLevel.NONE
                }
            }

            install(HttpTimeout) {
                // Match AssistantApiClient.requestTimeoutMs so HTTP doesn't cancel before Aimybox timeout
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
            }

            // Default headers and base URL
            defaultRequest {
                // Properly configure the base URL as a URL object
                url {
                    // Parse the base URL from BuildConfig
                    takeFrom(BuildConfig.API_BASE_URL)
                    // Ensure the URL ends with /
                    if (!BuildConfig.API_BASE_URL.endsWith("/")) {
                        encodedPath = encodedPath.let { if (it.isEmpty()) "/" else it }
                    }
                }

                // Add default headers
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }
            }

            // Handle exceptions
            expectSuccess = true
            HttpResponseValidator {
                validateResponse { response ->
                    val statusCode = response.status.value
                    when (statusCode) {
                        in 300..399 -> throw RedirectResponseException(response, "Redirect")
                        in 400..499 -> throw ClientRequestException(response, "Client error")
                        in 500..599 -> throw ServerResponseException(response, "Server error")
                    }
                }
            }
        }
    }

    /**
     * Provides an authenticated HttpClient with token refresh capabilities.
     * Uses Provider<> to break dependency cycles.
     */
    @Provides
    @Singleton
    @AuthenticatedHttpClient
    fun provideAuthenticatedHttpClient(
        @BaseHttpClient baseClient: HttpClient,
        authRepositoryProvider: Provider<AuthRepository>,
        authManagerProvider: Provider<AuthManager>
    ): HttpClient {
        return baseClient.config {
            // Install Auth plugin for token refresh
            install(Auth) {
                bearer {
                    // Load the token when the plugin is installed
                    loadTokens {
                        val authRepository = authRepositoryProvider.get()
                        val accessToken = runBlocking { authRepository.getAccessToken() }
                        val refreshToken = runBlocking { authRepository.getRefreshToken() }

                        if (accessToken != null && refreshToken != null) {
                            BearerTokens(accessToken, refreshToken)
                        } else {
                            null
                        }
                    }

                    // Send the token with each request
                    sendWithoutRequest { request ->
                        // Don't send token for authentication requests (login, register, refresh)
                        !request.url.encodedPath.contains("/api/v1/auth/login") &&
                        !request.url.encodedPath.contains("/api/v1/auth/register") &&
                        !request.url.encodedPath.contains("/api/v1/auth/google") &&
                        !request.url.encodedPath.contains("/api/v1/auth/refresh")
                    }

                    // Handle 401 responses by refreshing the token using AuthManager
                    refreshTokens {
                        try {
                            val authManager = authManagerProvider.get()
                            // Use the centralized AuthManager for token refresh
                            val refreshSuccessful = runBlocking { authManager.verifyAuthentication() }

                            if (refreshSuccessful) {
                                val authRepository = authRepositoryProvider.get()
                                // Get the updated tokens
                                val newAccessToken = runBlocking { authRepository.getAccessToken() }
                                val newRefreshToken = runBlocking { authRepository.getRefreshToken() }

                                if (newAccessToken != null && newRefreshToken != null) {
                                    BearerTokens(newAccessToken, newRefreshToken)
                                } else {
                                    throw UnauthorizedAccessException("Token refresh failed: tokens are null")
                                }
                            } else {
                                throw UnauthorizedAccessException("Token refresh failed")
                            }
                        } catch (e: Exception) {
                            // The AuthManager will emit AUTH_ERROR event which will be handled by ViewModels
                            throw e
                        }
                    }
                }
            }
        }
    }

    @Provides
    @Singleton
    fun provideAuthApiClient(@BaseHttpClient client: HttpClient, json: Json): AuthApiClient {
        return AuthApiClient(client, json)
    }

    @Provides
    @Singleton
    fun provideContactService(@ApplicationContext context: Context): ContactService {
        return ContactService(context)
    }

    @Provides
    @Singleton
    fun provideCallContactSkill(
        @ApplicationContext context: Context,
        contactService: ContactService,
        patchResponseCoordinator: PatchResponseCoordinator
    ): CallContactSkill {
        return CallContactSkill(context, contactService, patchResponseCoordinator)
    }

    @Provides
    @Singleton
    fun provideSendMessageSkill(
        @ApplicationContext context: Context,
        contactService: ContactService,
        smsPermissionRequestCoordinator: SmsPermissionRequestCoordinator
    ): SendMessageSkill {
        return SendMessageSkill(context, contactService, smsPermissionRequestCoordinator)
    }

    @Provides
    @Singleton
    fun provideCustomSkills(
        callContactSkill: CallContactSkill,
        sendMessageSkill: SendMessageSkill
    ): LinkedHashSet<CustomSkill<*, *>> {
        val skills = linkedSetOf<CustomSkill<*, *>>()
        skills.add(callContactSkill)
        skills.add(sendMessageSkill)
        return skills
    }

    @Provides
    @Singleton
    fun provideAssistantApiClient(
        @AuthenticatedHttpClient client: HttpClient,
        authRepository: AuthRepository,
        patchResponseCoordinator: PatchResponseCoordinator,
        customSkills: LinkedHashSet<CustomSkill<*, *>>
    ): AssistantApiClient {
        return AssistantApiClient(client, authRepository, patchResponseCoordinator, customSkills)
    }
}
