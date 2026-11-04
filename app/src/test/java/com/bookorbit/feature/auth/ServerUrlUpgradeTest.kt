package com.bookorbit.feature.auth

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlUpgradeTest {
    private fun upgrade(configured: String, finalUrl: String) =
        httpsUpgradeOf(configured, finalUrl.toHttpUrl())

    @Test
    fun upgradesPlainHostToHttps() {
        assertEquals(
            "https://books.example.com",
            upgrade("http://books.example.com", "https://books.example.com/api/v1/auth/setup-status"),
        )
    }

    @Test
    fun keepsSubpath() {
        assertEquals(
            "https://example.com/books",
            upgrade("http://example.com/books", "https://example.com/books/api/v1/auth/setup-status"),
        )
    }

    @Test
    fun keepsNonDefaultHttpsPort() {
        assertEquals(
            "https://example.com:8443",
            upgrade("http://example.com:8080", "https://example.com:8443/api/v1/auth/setup-status"),
        )
    }

    @Test
    fun ignoresAlreadyHttpsAndUnchangedHttp() {
        assertNull(upgrade("https://example.com", "https://example.com/api/v1/auth/setup-status"))
        assertNull(upgrade("http://192.168.1.50:3000", "http://192.168.1.50:3000/api/v1/auth/setup-status"))
    }

    @Test
    fun neverAdoptsAnotherHost() {
        assertNull(upgrade("http://books.example.com", "https://evil.example.net/api/v1/auth/setup-status"))
    }

    @Test
    fun neverAdoptsAnotherPath() {
        assertNull(upgrade("http://books.example.com", "https://books.example.com/login/api/v1/auth/setup-status"))
        assertNull(upgrade("http://books.example.com", "https://books.example.com/somewhere-else"))
    }

    @Test
    fun invalidConfiguredUrlIsIgnored() {
        assertNull(upgrade("not a url", "https://books.example.com/api/v1/auth/setup-status"))
    }
}
