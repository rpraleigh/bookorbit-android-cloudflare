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
 *
 * Cloudflare may instead answer a rejected or expired token with a 401/403 HTML page. BookOrbit
 * itself only answers API errors as JSON, so an HTML 401/403 on an `/api/` path is treated the same
 * way (see [isLikelyAccessBlock]). That second signal is a heuristic and has not been confirmed
 * against a real rejected token; verify it before relying on it.
 *
 * Each rejection is also reported to [CloudflareAccessEvents] so the app can offer to fix the token.
 */
@Singleton
class CloudflareAccessInterceptor internal constructor(
    private val session: SessionManager,
    private val httpsOnly: Boolean = true,
    private val events: CloudflareAccessEvents? = null,
) : Interceptor {
    @Inject constructor(session: SessionManager, events: CloudflareAccessEvents) :
        this(session, httpsOnly = true, events = events)

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
                events?.notifyRejected(configured)
                throw CloudflareAccessException(configured)
            }
        }
        if (isLikelyAccessBlock(request.url.encodedPath, response.code, response.header("Content-Type"))) {
            response.close()
            events?.notifyRejected(configured)
            throw CloudflareAccessException(configured)
        }
        return response
    }

    companion object {
        const val HEADER_CLIENT_ID = "CF-Access-Client-Id"
        const val HEADER_CLIENT_SECRET = "CF-Access-Client-Secret"

        /**
         * True for a 401/403 HTML page on an API path. BookOrbit's own API errors are JSON, so an HTML
         * error there came from something in front of it, which here means Cloudflare Access.
         */
        internal fun isLikelyAccessBlock(encodedPath: String, status: Int, contentType: String?): Boolean =
            (status == 401 || status == 403) &&
                encodedPath.contains("/api/") &&
                contentType?.trimStart()?.startsWith("text/html", ignoreCase = true) == true

        internal fun isAccessLoginHost(host: String): Boolean =
            host.equals("cloudflareaccess.com", ignoreCase = true) ||
                host.endsWith(".cloudflareaccess.com", ignoreCase = true)
    }
}
