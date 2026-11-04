package com.bookorbit.core.network

import com.bookorbit.core.auth.SessionManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Cloudflare Access turned the request away (it redirected to its login page). */
class CloudflareAccessException(val credentialsConfigured: Boolean) :
    IOException("Cloudflare Access rejected the request (service token configured: $credentialsConfigured)")

/**
 * Adds the Cloudflare Access service-token headers (`CF-Access-Client-Id` / `CF-Access-Client-Secret`)
 * to requests for the configured server's host.
 *
 * The headers are sent only over HTTPS, so the secret never crosses the network in plain text (for
 * example on the first hop of an `http://` address that redirects to `https://`).
 *
 * Install with `addNetworkInterceptor`, NOT `addInterceptor`. Network interceptors run once per hop,
 * so the host check is repeated for every redirect. An application interceptor would attach the
 * headers once and OkHttp would then carry them to a different host on a cross-host redirect (OkHttp
 * only strips `Authorization` there), leaking the service-token secret.
 *
 * When Access is not satisfied it answers with a redirect to `<team>.cloudflareaccess.com`; that is
 * turned into a [CloudflareAccessException] so callers get a clear error instead of an HTML login
 * page that fails JSON parsing.
 */
@Singleton
class CloudflareAccessInterceptor internal constructor(
    private val session: SessionManager,
    private val httpsOnly: Boolean,
) : Interceptor {
    @Inject constructor(session: SessionManager) : this(session, httpsOnly = true)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val serverHost = session.serverUrl?.toHttpUrlOrNull()?.host
        if (serverHost == null || !request.url.host.equals(serverHost, ignoreCase = true)) {
            return chain.proceed(request)
        }

        val id = session.cfClientId
        val secret = session.cfClientSecret
        val configured = !id.isNullOrBlank() && !secret.isNullOrBlank()
        val outgoing = if (configured && (!httpsOnly || request.isHttps)) {
            request.newBuilder()
                .header(HEADER_CLIENT_ID, id!!)
                .header(HEADER_CLIENT_SECRET, secret!!)
                .build()
        } else {
            request
        }

        val response = chain.proceed(outgoing)
        if (response.isRedirect) {
            val target = response.header("Location")?.let { request.url.resolve(it) }
            if (target != null && isAccessLoginHost(target.host)) {
                response.close()
                throw CloudflareAccessException(configured)
            }
        }
        return response
    }

    companion object {
        const val HEADER_CLIENT_ID = "CF-Access-Client-Id"
        const val HEADER_CLIENT_SECRET = "CF-Access-Client-Secret"

        internal fun isAccessLoginHost(host: String): Boolean =
            host.equals("cloudflareaccess.com", ignoreCase = true) ||
                host.endsWith(".cloudflareaccess.com", ignoreCase = true)
    }
}
