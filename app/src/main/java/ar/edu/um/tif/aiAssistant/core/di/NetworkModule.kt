package ar.edu.um.tif.aiAssistant.core.di

import ar.edu.um.tif.aiAssistant.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Singleton

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
    }

    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient {
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

            install(Logging) {
                logger = Logger.DEFAULT
                level = if (BuildConfig.DEBUG) {
                    LogLevel.ALL
                } else {
                    LogLevel.NONE
                }
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 30000
                socketTimeoutMillis = 30000
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
                validateResponse suspend@{ response ->
                    val statusCode = response.status.value
                    val responseText = response.bodyAsText()
                    when (statusCode) {
                        in 300..399 -> throw RedirectResponseException(response, responseText)
                        in 400..499 -> throw ClientRequestException(response, responseText)
                        in 500..599 -> throw ServerResponseException(response, responseText)
                    }
                }
            }
        }
    }
}
