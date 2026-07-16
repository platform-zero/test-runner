package org.webservices.testrunner

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.webservices.testrunner.framework.AuthHelper
import org.webservices.testrunner.framework.AuthResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnhancedAuthenticationTestsTest {

    @Test
    fun `direct password login reports the keycloak browser contract`() = runTest {
        val result = AuthHelper(mockClient()).login("testuser", "testpass123")

        val error = assertIs<AuthResult.Error>(result)
        assertEquals(
            "Direct API login is not supported; Keycloak login is browser-driven and tested by Playwright.",
            error.message,
        )
    }

    @Test
    fun `direct password login still validates credentials before rejecting the flow`() = runTest {
        val result = AuthHelper(mockClient()).login("", "testpass123")

        val error = assertIs<AuthResult.Error>(result)
        assertContains(error.message, "Username cannot be blank")
    }

    @Test
    fun `trusted internal session exposes one exact cookie and logout clears it`() {
        val auth = AuthHelper(mockClient())

        val result = auth.trustInternalUser("alice", listOf("users", "operators"))
        val success = assertIs<AuthResult.Success>(result)
        assertEquals("trusted_internal_edge_auth", success.sessionCookie.name)
        assertEquals("alice", success.sessionCookie.value)
        assertTrue(success.sessionCookie.httpOnly)
        assertTrue(success.sessionCookie.secure)
        assertTrue(auth.isAuthenticated())
        assertEquals(success.sessionCookie, auth.getSessionCookie())

        auth.logout()

        assertFalse(auth.isAuthenticated())
        assertNull(auth.getSessionCookie())
    }

    @Test
    fun `auth verification requires exact 200 and the trusted username in the body`() = runTest {
        assertTrue(verifier(HttpStatusCode.OK, "signed in as alice").verifyAuth())
        assertFalse(verifier(HttpStatusCode.OK, "signed in as bob").verifyAuth())
        assertFalse(verifier(HttpStatusCode.Found, "signed in as alice").verifyAuth())
        assertFalse(verifier(HttpStatusCode.Unauthorized, "signed in as alice").verifyAuth())
    }

    private fun verifier(status: HttpStatusCode, body: String): AuthHelper =
        AuthHelper(
            client = mockClient(),
            requestClient = mockClient(status, body),
            protectedServiceUrl = "https://service.example.test/",
        ).also { it.trustInternalUser("alice", listOf("users")) }

    private fun mockClient(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "{}",
    ): HttpClient = HttpClient(MockEngine) {
        followRedirects = false
        engine {
            addHandler {
                respond(
                    content = body,
                    status = status,
                    headers = headersOf("Content-Type", "application/json"),
                )
            }
        }
    }
}
