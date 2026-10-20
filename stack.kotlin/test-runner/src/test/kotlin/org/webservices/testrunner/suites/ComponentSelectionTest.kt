package org.webservices.testrunner.suites

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Source/filesystem authority tests only, never application authentication. */
class ComponentSelectionTest {
    @TempDir lateinit var directory: Path

    private fun lock(name: String, content: String) = directory.resolve(name).toFile().also { it.writeText(content) }

    @Test fun `explicit valid lock has priority over fallback`() {
        val selected = lock("selected.json", """{"components":["core","firefly-iii"]}""")
        val fallback = lock("fallback.json", """{"components":["vaultwarden"]}""")
        assertEquals(setOf("core", "firefly-iii"), loadSelectedComponentNames(
            mapOf("TEST_RUNNER_COMPONENTS_LOCK_FILE" to selected.path, "WEBSERVICES_COMPONENTS_LOCK_FILE" to fallback.path),
            listOf(fallback),
        ))
    }

    @Test fun `explicit missing or blank authority cannot fall back`() {
        val fallback = lock("fallback.json", """{"components":["vaultwarden"]}""")
        for (value in listOf(directory.resolve("missing.json").toString(), "", " ")) {
            assertFailsWith<IllegalStateException> {
                loadSelectedComponentNames(mapOf("TEST_RUNNER_COMPONENTS_LOCK_FILE" to value), listOf(fallback))
            }
        }
    }

    @Test fun `malformed explicit authority cannot fall back`() {
        val fallback = lock("fallback.json", """{"components":["vaultwarden"]}""")
        val bad = lock("bad.json", "{" )
        assertFailsWith<IllegalStateException> {
            loadSelectedComponentNames(mapOf("TEST_RUNNER_COMPONENTS_LOCK_FILE" to bad.path), listOf(fallback))
        }
    }

    @Test fun `invalid schemas entries duplicates or empty authority refuse`() {
        for (document in listOf(
            "{", "null", "[]", "{}", """{"components":"core"}""", """{"components":[]}""",
            """{"components":["core",1]}""", """{"components":["core",null]}""",
            """{"components":["core","core"]}""", """{"components":[""]}""",
            """{"components":[" core"]}""", """{"components":["invalid_id"]}""",
        )) assertFailsWith<IllegalStateException> { parseSelectedComponentNames(document) }
    }

    @Test fun `parser failure never echoes document content`() {
        val sentinel = "PRIVATE_DOCUMENT_MUST_NOT_APPEAR"
        val failure = assertFailsWith<IllegalStateException> { parseSelectedComponentNames(sentinel) }
        assertEquals("Invalid component selection authority", failure.message)
        assertNull(failure.cause)
    }

    @Test fun `known fallback authority is parsed strictly`() {
        val fallback = lock("fallback.json", """{"components":["core","jupyterhub","forgejo-runner"]}""")
        assertEquals(setOf("core", "jupyterhub", "forgejo-runner"), loadSelectedComponentNames(emptyMap(), listOf(fallback)))
        fallback.writeText("{}")
        assertFailsWith<IllegalStateException> { loadSelectedComponentNames(emptyMap(), listOf(fallback)) }
    }

    @Test fun `linked and oversized authorities refuse`() {
        val target = lock("target.json", """{"components":["core"]}""")
        val link = directory.resolve("linked.json")
        java.nio.file.Files.createSymbolicLink(link, target.toPath())
        assertFailsWith<IllegalStateException> {
            loadSelectedComponentNames(mapOf("TEST_RUNNER_COMPONENTS_LOCK_FILE" to link.toString()), emptyList())
        }
        target.writeText(" ".repeat(1024 * 1024 + 1))
        assertFailsWith<IllegalStateException> {
            loadSelectedComponentNames(mapOf("TEST_RUNNER_COMPONENTS_LOCK_FILE" to target.path), emptyList())
        }
    }

    @Test fun `only unconfigured absent defaults preserve reusable legacy catalog`() {
        assertNull(loadSelectedComponentNames(emptyMap(), listOf(directory.resolve("absent.json").toFile())))
    }
}
