package org.webservices.testrunner

import org.webservices.testrunner.suites.ManagedSuiteCategory
import org.webservices.testrunner.suites.managedSuiteRegistry
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestArchitectureTest {
    @Test
    fun `canonical Kotlin suite names are explicit and stable`() {
        assertEquals(
            listOf(
                "stack-core",
                "stack-auth",
                "stack-apps",
                "stack-contract",
                "stack-live-ingestion",
                "stack-recovery",
                "stack-full",
                "kotlin-all",
            ),
            SuiteCatalog.availableSuiteNames(),
        )
        assertEquals(DEFAULT_SUITE, SuiteCatalog.resolve(LEGACY_DEFAULT_SUITE)?.name)
        assertEquals(KOTLIN_ALL_SUITE, SuiteCatalog.resolve(LEGACY_ALL_SUITE)?.name)
    }

    @Test
    fun `managed leaf registry has unique ids and no empty advertised category`() {
        val ids = managedSuiteRegistry.map { it.id }

        assertEquals(ids.size, ids.toSet().size, "Managed suite ids must be unique")
        ManagedSuiteCategory.entries.forEach { category ->
            assertTrue(
                managedSuiteRegistry.any { it.category == category },
                "Advertised managed suite category $category must contain a leaf suite",
            )
        }
        assertTrue(managedSuiteRegistry.all { it.requiredComponents.none(String::isBlank) })
    }

    @Test
    fun `every managed leaf suite implementation is registered exactly once`() {
        val declarationPattern = Regex("suspend fun TestRunner\\.([A-Za-z0-9_]+Tests)\\s*\\(")
        val registrationPattern = Regex("\\{\\s*([A-Za-z0-9_]+Tests)\\(\\)\\s*}")
        val aggregateFunctions = setOf(
            "stackCoreTests",
            "stackAuthTests",
            "stackAppTests",
            "stackContractTests",
            "stackLiveIngestionTests",
            "webServicesTests",
            "stackFullTests",
            "recoveryTests",
        )

        val suiteSources = buildList {
            Files.list(TestSourceFiles.modulesRoot()).use { modules ->
                modules.filter(Files::isDirectory).forEach { module ->
                    add(module.resolve("stack.kotlin/test-runner/src/main/kotlin/org/webservices/testrunner/suites"))
                }
            }
        }
        val declared = suiteSources
            .filter(Files::isDirectory)
            .flatMap { directory ->
                Files.walk(directory).use { files ->
                    files
                        .filter(Files::isRegularFile)
                        .filter { it.extension == "kt" }
                        .map(Files::readString)
                        .toList()
                }
            }
            .flatMap { source -> declarationPattern.findAll(source).map { it.groupValues[1] }.toList() }
            .filterNot(aggregateFunctions::contains)
            .toSet()
        val registrySource = TestSourceFiles.moduleText(
            "test-runner",
            "stack.kotlin/test-runner/src/main/kotlin/org/webservices/testrunner/suites/ManagedSuiteRegistry.kt",
        )
        val registered = registrationPattern.findAll(registrySource).map { it.groupValues[1] }.toList()

        assertEquals(registered.size, registered.toSet().size, "A managed leaf implementation is registered more than once")
        assertEquals(declared, registered.toSet(), "Managed leaf declarations and registry entries must match")
    }

    @Test
    fun `deployed all runner retains source Kotlin and complete browser tiers`() {
        val runner = TestSourceFiles.repositoryText("stack.containers/test-runner/run-tests.sh")
        val entrypoint = TestSourceFiles.repositoryText("stack.containers/test-runner/container-entrypoint.sh")

        listOf("kt-full", "ts-unit", "ts-e2e-all").forEach { assertTrue(runner.contains(it)) }
        listOf("boundary", "app-smoke", "sso", "mobile", "workflow", "visual").forEach {
            assertTrue(entrypoint.contains(it), "Playwright all orchestration must include $it")
        }
    }
}
