package org.webservices.testrunner

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class PurgeScriptConfigTest {
    @Test
    fun `webservices purge covers systemd and labware workspace resources`() {
        val purge = repoFileText("ops/host-admin/purge-webservices-stack.sh")

        assertTrue(purge.contains("systemctl --user"))
        assertTrue(purge.contains("webservices.target"))
        assertTrue(purge.contains("--print-only"))
        assertTrue(purge.contains("EXPECTED_HOSTNAME"))
        assertTrue(purge.contains("--yes-delete-webservices-stack"))
        assertTrue(purge.contains("print_container_targets"))
        assertTrue(purge.contains("label=org.platform-zero.runtime.project=\$STACK_PROJECT_NAME"))
        assertTrue(purge.contains("\${STACK_PROJECT_NAME}_ name prefix"))
        assertTrue(purge.contains("list_target_volume_names"))
        assertTrue(purge.contains("index(\$0, prefix) == 1"))
        assertTrue(purge.contains("\"${'$'}CONTAINER_CLI\" network rm"))
        assertTrue(purge.contains("\"${'$'}CONTAINER_CLI\" volume rm"))
        assertTrue(purge.contains("LABWARE_CONTAINER_HOST=\"\${LABWARE_CONTAINER_HOST:-unix:///run/labware/podman.sock}\""))
        assertTrue(purge.contains("label=webservices.workspace.id"))
        assertTrue(purge.contains("label=webservices.test.tenant.id"))
        assertTrue(purge.contains("purge_labware_runtime"))
    }

    @Test
    fun `site storage purge matches hardcoded external storage roots`() {
        val purge = repoFileText("ops/host-admin/purge-site-storage-dirs.sh")

        listOf(
            "/mnt/stack/pg-ssd/postgres-ssd",
            "/mnt/stack/volumes/postgres_data",
            "/mnt/stack/volumes/mariadb_data",
            "/mnt/stack/vector-dbs/qdrant",
            "/mnt/stack/vector-dbs/opensearch",
            "/mnt/media/seafile-media",
        ).forEach { path ->
            assertTrue(purge.contains("\"$path\""), "storage purge should include $path")
            assertTrue(purge.contains(path), "storage purge validator should allow $path")
        }
        assertTrue(purge.contains("rm -rf --one-file-system --"))
        assertTrue(purge.contains("--yes-delete-site-storage"))
        assertTrue(purge.contains("EXPECTED_HOSTNAME"))
    }

    private fun repoFileText(relativePath: String): String = TestSourceFiles.repositoryText(relativePath)

    private fun repoRoot(): Path {
        var current = Path.of("").toAbsolutePath()
        while (!Files.exists(current.resolve("BUILD.bazel"))) {
            current = current.parent ?: error("repo root not found")
        }
        return current
    }
}
