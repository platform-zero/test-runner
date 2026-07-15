package org.webservices.testrunner

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class StackDeploymentHelpersTest {
    private fun repoFile(path: String): Path = TestSourceFiles.repositoryPath(path)

    @Test
    fun `runtime root lives in user tmpfs`() {
        val runtimeRoot = "/run/user/1000/webservices-runtime"
        assertTrue(runtimeRoot.startsWith("/run/user/"))
        assertTrue(runtimeRoot.endsWith("/webservices-runtime"))
    }

    @Test
    fun `podman systemd command construction targets generated stack units`() {
        val unit = "webservices.target"

        val command = listOf(
            "systemctl",
            "start",
            unit
        )

        assertEquals("systemctl", command[0])
        assertEquals("start", command[1])
        assertEquals(unit, command[2])
    }

    @Test
    fun `podman ps command for readiness`() {
        val service = "postgres"
        val command = listOf(
            "podman",
            "ps",
            "--filter",
            "name=webservices-$service",
            "--format",
            "{{.Names}} {{.Status}}"
        )

        assertTrue(command.contains("ps"))
        assertTrue(command.contains("name=webservices-$service"))
        assertTrue(command.contains("--format"))
    }

    @Test
    fun `required bundle files list is correct`() {
        val requiredFiles = listOf(
            "stack.ir.json",
            "build/site/manifest.json",
            "build/build-info.json"
        )

        assertEquals(3, requiredFiles.size)
        assertTrue(requiredFiles.contains("stack.ir.json"))
        assertTrue(requiredFiles.contains("build/site/manifest.json"))
        assertTrue(requiredFiles.contains("build/build-info.json"))
    }

    @Test
    fun `deploy reloads active units when rebuilt local images change`() {
        val deployScript = Files.readString(repoFile("scripts/deploy.sh"))

        assertTrue(
            deployScript.contains("snapshot_built_image_ids_before"),
            "Deploy should capture built image IDs before rebuilding"
        )
        assertTrue(
            deployScript.contains("reload_changed_built_image_units"),
            "Deploy should reload active service units whose rebuilt image ID changed"
        )
        assertTrue(
            deployScript.contains("container_image_id_for_service"),
            "Deploy should also reload stale containers that still point at an older rebuilt image"
        )
        assertTrue(
            deployScript.contains("test(\":local-build$\")"),
            "Deploy should treat local-build tagged images as rebuildable service images"
        )
        assertTrue(
            deployScript.contains("unit_for_runtime_service"),
            "Deploy should map runtime services to their systemd lifecycle unit before reloading"
        )
    }

    @Test
    fun `test runner search client queries OpenSearch`() {
        val serviceClient = Files.readString(repoFile("stack.kotlin/test-runner/src/main/kotlin/org/webservices/testrunner/framework/ServiceClient.kt"))
        val searchPostIndex = serviceClient.indexOf("knowledge/_search")
        val contentTypeIndex = serviceClient.indexOf("contentType(ContentType.Application.Json)", searchPostIndex)
        val authIndex = serviceClient.indexOf("HttpHeaders.Authorization", searchPostIndex)

        assertTrue(searchPostIndex >= 0, "ServiceClient should post searches to OpenSearch")
        assertTrue(authIndex in searchPostIndex until contentTypeIndex, "Search requests should include OpenSearch basic auth")
    }

    @Test
    fun `forgejo runner ssh mount is dedicated private and read only`() {
        val runtime = TestSourceFiles.moduleText("forgejo-runner", "stack.runtime.yaml")

        assertTrue(
            runtime.contains("/mnt/stack/forgejo-runner-ssh:/root/.ssh:ro"),
            "Forgejo runner must mount only its dedicated SSH directory and must mount it read-only"
        )
        assertFalse(runtime.contains(":/root/.ssh") && !runtime.contains("forgejo-runner-ssh:/root/.ssh:ro"))
    }

    @Test
    fun `mastodon stack targets postgres ssd across all roles`() {
        val mastodonRuntime = TestSourceFiles.moduleText("mastodon", "stack.runtime.yaml")
        val mastodonEnv = Files.readString(repoFile("stack.config/mastodon/mastodon.env"))

        assertTrue(
            mastodonRuntime.contains("postgres-ssd-bootstrap: \"completed\""),
            "Mastodon services should wait for postgres-ssd bootstrap completion before starting"
        )
        assertTrue(
            mastodonRuntime.contains("DB_HOST: postgres-ssd"),
            "Mastodon runtime should point every role at postgres-ssd"
        )
        assertTrue(
            mastodonEnv.contains("DB_HOST=postgres-ssd"),
            "Mastodon env file should align with the postgres-ssd target"
        )
    }

    @Test
    fun `status output parsing detects healthy status`() {
        val healthyStatuses = listOf(
            "Up 2 minutes (healthy)",
            "Up About a minute (healthy)",
            "Up 30 seconds (healthy)"
        )

        healthyStatuses.forEach { status ->
            assertTrue(status.contains("healthy", ignoreCase = true))
        }
    }

    @Test
    fun `status output parsing detects unhealthy status`() {
        val unhealthyStatuses = listOf(
            "Up 2 minutes (unhealthy)",
            "Up About a minute (health: starting)",
            "Exited (1)"
        )

        unhealthyStatuses.forEach { status ->
            assertFalse(status.contains("healthy", ignoreCase = true) && !status.contains("unhealthy"))
        }
    }
}
