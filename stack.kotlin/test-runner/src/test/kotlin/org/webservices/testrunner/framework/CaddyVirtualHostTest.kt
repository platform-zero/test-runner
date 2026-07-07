package org.webservices.testrunner.framework

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaddyVirtualHostTest {

    @Test
    fun `https virtual host requests rewrite the request host instead of sending a conflicting host header`() {
        val builder = HttpRequestBuilder().apply {
            url("https://auth.example.com/api/config")
        }

        builder.applyCaddyVirtualHost("portal.example.com")

        assertEquals("https", builder.url.protocol.name)
        assertEquals("portal.example.com", builder.url.host)
        assertEquals("https://portal.example.com/api/config", builder.url.toString())
        assertNull(builder.headers[HttpHeaders.Host])
        assertNull(builder.headers["X-Forwarded-Host"])
        assertNull(builder.headers["X-Forwarded-Proto"])
    }

    @Test
    fun `http virtual host requests preserve transport target and forward the desired host explicitly`() {
        val builder = HttpRequestBuilder().apply {
            url("http://caddy:80/api/config")
        }

        builder.applyCaddyVirtualHost("portal.example.com")

        assertEquals("http", builder.url.protocol.name)
        assertEquals("caddy", builder.url.host)
        assertEquals("http://caddy/api/config", builder.url.toString())
        assertEquals("portal.example.com", builder.headers[HttpHeaders.Host])
        assertEquals("portal.example.com", builder.headers["X-Forwarded-Host"])
        assertEquals("https", builder.headers["X-Forwarded-Proto"])
    }
}
