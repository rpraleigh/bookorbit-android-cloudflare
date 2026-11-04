package com.bookorbit.core.network

import com.bookorbit.core.auth.SessionManager
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class CloudflareAccessInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var other: MockWebServer
    private val session = mockk<SessionManager>()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        other = MockWebServer().also { it.start() }
        every { session.serverUrl } returns "http://localhost:${server.port}"
        every { session.cfClientId } returns "id.access"
        every { session.cfClientSecret } returns "s3cret"
    }

    @After
    fun tearDown() {
        server.shutdown()
        other.shutdown()
    }

    /** The configured server is addressed as `localhost`; the "other" server as `127.0.0.1`. */
    private fun local(path: String) = "http://localhost:${server.port}$path"
    private fun foreign(path: String) = "http://127.0.0.1:${other.port}$path"

    private fun client() = OkHttpClient.Builder()
        .addNetworkInterceptor(CloudflareAccessInterceptor(session, httpsOnly = false))
        .build()

    @Test
    fun attachesHeadersToServerHost() {
        server.enqueue(MockResponse().setBody("{}"))
        client().newCall(Request.Builder().url(local("/api/v1/x")).build()).execute().close()
        val recorded = server.takeRequest()
        assertEquals("id.access", recorded.getHeader("CF-Access-Client-Id"))
        assertEquals("s3cret", recorded.getHeader("CF-Access-Client-Secret"))
    }

    @Test
    fun neverSendsSecretOverPlainHttpByDefault() {
        val defaultClient = OkHttpClient.Builder()
            .addNetworkInterceptor(CloudflareAccessInterceptor(session))
            .build()
        server.enqueue(MockResponse().setBody("{}"))
        defaultClient.newCall(Request.Builder().url(local("/x")).build()).execute().close()
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("CF-Access-Client-Id"))
        assertNull(recorded.getHeader("CF-Access-Client-Secret"))
    }

    @Test
    fun omitsHeadersWhenNotConfigured() {
        every { session.cfClientId } returns null
        every { session.cfClientSecret } returns null
        server.enqueue(MockResponse().setBody("{}"))
        client().newCall(Request.Builder().url(local("/x")).build()).execute().close()
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("CF-Access-Client-Id"))
        assertNull(recorded.getHeader("CF-Access-Client-Secret"))
    }

    @Test
    fun neverSendsHeadersToAnotherHost() {
        val foreignUrl = foreign("/")
        other.enqueue(MockResponse().setBody("{}"))
        client().newCall(Request.Builder().url(foreignUrl).build()).execute().close()
        val recorded = other.takeRequest()
        assertNull(recorded.getHeader("CF-Access-Client-Id"))
        assertNull(recorded.getHeader("CF-Access-Client-Secret"))
    }

    @Test
    fun doesNotForwardHeadersAcrossCrossHostRedirect() {
        val target = foreign("/landing")
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", target.toString()))
        other.enqueue(MockResponse().setBody("ok"))
        client().newCall(Request.Builder().url(local("/go")).build()).execute().close()
        assertEquals("id.access", server.takeRequest().getHeader("CF-Access-Client-Id"))
        val hop = other.takeRequest()
        assertNull(hop.getHeader("CF-Access-Client-Id"))
        assertNull(hop.getHeader("CF-Access-Client-Secret"))
    }

    @Test
    fun redirectToAccessLoginThrowsWithCredentialsFlag() {
        server.enqueue(
            MockResponse().setResponseCode(302)
                .setHeader("Location", "https://myteam.cloudflareaccess.com/cdn-cgi/access/login/x"),
        )
        try {
            client().newCall(Request.Builder().url(local("/api/v1/setup-status")).build()).execute()
            fail("expected CloudflareAccessException")
        } catch (e: CloudflareAccessException) {
            assertTrue(e.credentialsConfigured)
        }
    }

    @Test
    fun redirectToAccessLoginReportsMissingCredentials() {
        every { session.cfClientId } returns null
        every { session.cfClientSecret } returns null
        server.enqueue(
            MockResponse().setResponseCode(302)
                .setHeader("Location", "https://myteam.cloudflareaccess.com/login"),
        )
        try {
            client().newCall(Request.Builder().url(local("/x")).build()).execute()
            fail("expected CloudflareAccessException")
        } catch (e: CloudflareAccessException) {
            assertFalse(e.credentialsConfigured)
        }
    }

    @Test
    fun accessLoginHostMatchIsStrict() {
        assertTrue(CloudflareAccessInterceptor.isAccessLoginHost("team.cloudflareaccess.com"))
        assertFalse(CloudflareAccessInterceptor.isAccessLoginHost("evilcloudflareaccess.com"))
        assertFalse(CloudflareAccessInterceptor.isAccessLoginHost("cloudflareaccess.com.evil.example"))
    }
}
