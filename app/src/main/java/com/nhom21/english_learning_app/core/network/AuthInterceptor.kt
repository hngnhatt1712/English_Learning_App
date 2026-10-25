package com.nhom21.english_learning_app.core.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that attaches Bearer tokens to outbound requests and handles
 * 401 unauthorized session invalidation per decision D-A8 and contract section 4.2.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
    private val sessionEvents: SessionEvents,
    private val errorParser: ApiErrorParser
) : Interceptor {

    companion object {
        private const val MAX_PEEK_BYTES = 64L * 1024L
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        // Public endpoints that must not receive the Authorization header
        val isPublicAuthEndpoint = path.endsWith("auth/login") || path.endsWith("auth/register")

        val request = if (!isPublicAuthEndpoint) {
            val token = tokenManager.currentTokenBlocking()
            if (!token.isNullOrBlank()) {
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                originalRequest
            }
        } else {
            originalRequest
        }

        val response = chain.proceed(request)

        if (response.code == 401) {
            val peekedString = response.peekBody(MAX_PEEK_BYTES).string()
            val failure = errorParser.parse(peekedString, 401)

            // Contract 4.2: Only error.type == "unauthorized" clears session
            if (failure.type == ApiErrorType.UNAUTHORIZED) {
                tokenManager.clearTokenBlocking()
                sessionEvents.notifySessionExpired()
            }
        }

        return response
    }
}
