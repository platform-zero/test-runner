package org.webservices.testrunner.suites

import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.webservices.testrunner.framework.TestRunner

suspend fun requireOkResponse(response: HttpResponse, label: String): String {
    require(response.status == HttpStatusCode.OK) {
        "$label must return 200 OK, got ${response.status}"
    }
    return response.bodyAsText()
}

suspend fun requireOkOrRedirectResponse(response: HttpResponse, label: String): String? {
    if (response.status == HttpStatusCode.OK) {
        val body = response.bodyAsText()
        require(body.isNotBlank()) { "$label returned 200 OK with an empty body" }
        return body
    }

    requireServiceRedirect(response, label)
    return null
}

fun requireAuthBoundary(response: HttpResponse, label: String) {
    if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) return

    val location = requireServiceRedirect(response, label)
    require(listOf("auth", "login", "oauth", "signin", "keycloak").any { location.contains(it, ignoreCase = true) }) {
        "$label redirected without an identifiable authentication destination: $location"
    }
}

suspend fun requireOkOrAuthBoundary(response: HttpResponse, label: String) {
    if (response.status == HttpStatusCode.OK) {
        require(response.bodyAsText().isNotBlank()) { "$label returned 200 OK with an empty body" }
        return
    }
    requireAuthBoundary(response, label)
}

private fun requireServiceRedirect(response: HttpResponse, label: String): String {
    require(response.status in setOf(
        HttpStatusCode.MovedPermanently,
        HttpStatusCode.Found,
        HttpStatusCode.SeeOther,
        HttpStatusCode.TemporaryRedirect,
        HttpStatusCode.PermanentRedirect,
    )) { "$label must return 200 OK or an explicit redirect, got ${response.status}" }

    val location = response.headers[HttpHeaders.Location]?.trim().orEmpty()
    require(location.isNotEmpty()) { "$label returned ${response.status} without a Location header" }
    require(location != response.call.request.url.toString()) { "$label redirects back to the same URL" }
    return location
}

suspend fun TestRunner.getMastodonInternalResponse(path: String): HttpResponse {
    val suffix = if (path.startsWith("/")) path else "/$path"
    val host = System.getenv("MASTODON_HOST_HEADER")
        ?.takeIf { it.isNotBlank() }
        ?: System.getenv("DOMAIN")?.takeIf { it.isNotBlank() }?.let { "mastodon.$it" }
        ?: "mastodon.localhost"

    return client.getRawResponse("${endpoints.mastodon.trimEnd('/')}$suffix") {
        header(HttpHeaders.Accept, "text/html,application/json,*/*")
        header(HttpHeaders.Host, host)
        header("X-Forwarded-Proto", "https")
    }
}
