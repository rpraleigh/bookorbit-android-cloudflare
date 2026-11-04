package com.bookorbit.feature.auth

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val SETUP_STATUS_PATH = "/api/v1/auth/setup-status"

/**
 * If the server was configured with plain `http://` but answered the setup probe from an `https://`
 * address (typically an HTTP-to-HTTPS redirect at the proxy), returns the HTTPS server URL to save.
 *
 * Deliberately narrow: the host and the base path must be unchanged and only the scheme (and
 * possibly the port) may differ. A redirect to another host, or to any other path, is never adopted,
 * so a redirect can't point the app (and its credentials) somewhere new.
 *
 * Why it matters: OkHttp turns a redirected POST into a GET, so sign-in would silently fail against
 * an `http://` address even though the setup probe (a GET) succeeds.
 *
 * @param configured the server URL as entered (no trailing slash)
 * @param probeFinalUrl the URL the setup-status response finally came from
 */
internal fun httpsUpgradeOf(configured: String, probeFinalUrl: HttpUrl): String? {
    val original = configured.toHttpUrlOrNull() ?: return null
    if (original.isHttps || !probeFinalUrl.isHttps) return null
    if (!original.host.equals(probeFinalUrl.host, ignoreCase = true)) return null

    val finalPath = probeFinalUrl.encodedPath
    if (!finalPath.endsWith(SETUP_STATUS_PATH)) return null
    val basePath = finalPath.removeSuffix(SETUP_STATUS_PATH)
    if (basePath != original.encodedPath.trimEnd('/')) return null

    return probeFinalUrl.newBuilder()
        .encodedPath(if (basePath.isEmpty()) "/" else "$basePath/")
        .query(null)
        .fragment(null)
        .build()
        .toString()
        .trimEnd('/')
}
