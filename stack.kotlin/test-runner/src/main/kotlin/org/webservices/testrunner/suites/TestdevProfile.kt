package org.webservices.testrunner.suites

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files

internal fun isTestdevProfile(): Boolean =
    System.getenv("TESTDEV_SKIP_GPU_INGESTION") == "1"

private val selectedComponentNames: Set<String>? by lazy { loadSelectedComponentNames() }

internal fun parseSelectedComponentNames(document: String): Set<String> = try {
    val root = Json.parseToJsonElement(document) as? JsonObject ?: error("not an object")
    val components = root["components"] as? JsonArray ?: error("not an array")
    check(components.isNotEmpty())
    val names = components.map { value ->
        val primitive = value as? JsonPrimitive ?: error("not a string")
        check(primitive.isString && primitive.content.matches(Regex("[a-z0-9][a-z0-9-]*")))
        primitive.content
    }
    check(names.size == names.toSet().size)
    names.toSet()
} catch (_: Exception) {
    // Do not echo a mistakenly mounted document or parser's input excerpt.
    throw IllegalStateException("Invalid component selection authority")
}

internal fun loadSelectedComponentNames(
    environment: Map<String, String> = System.getenv(),
    defaultCandidates: List<File> = listOf(
        File("/component-lock/components.lock.json"),
        File("/runtime/components.lock.json"),
        File("/app/build/site/components.lock.json"),
        File("/app/site/components.lock.json"),
    ),
): Set<String>? {
    val explicitKey = listOf("TEST_RUNNER_COMPONENTS_LOCK_FILE", "WEBSERVICES_COMPONENTS_LOCK_FILE")
        .firstOrNull(environment::containsKey)
    val lockFile = if (explicitKey != null) {
        val path = environment.getValue(explicitKey)
        check(path.isNotBlank()) { "Explicit component selection authority is missing or unreadable" }
        File(path).also {
            check(it.isFile && it.canRead()) { "Explicit component selection authority is missing or unreadable" }
        }
    } else {
        defaultCandidates.firstOrNull { it.isFile } ?: return null
    }
    check(!Files.isSymbolicLink(lockFile.toPath()) && lockFile.canRead() && lockFile.length() <= 1024 * 1024) {
        "Linked, unreadable or oversized component selection authority"
    }
    val document = try { lockFile.readText() } catch (_: Exception) {
        throw IllegalStateException("Cannot read component selection authority")
    }
    return parseSelectedComponentNames(document)
}

internal fun selectedComponentsKnown(): Boolean =
    selectedComponentNames != null

internal fun isSelectedComponent(component: String): Boolean =
    selectedComponentNames?.contains(component) ?: true

internal fun isAnySelectedComponent(vararg components: String): Boolean =
    selectedComponentNames?.let { selected -> components.any(selected::contains) } ?: true

internal fun skipUnselectedComponent(component: String, description: String): Boolean {
    if (isSelectedComponent(component)) {
        return false
    }
    println("      ✓ $description intentionally excluded because component '$component' is not selected")
    return true
}

internal fun skipUnlessAnySelectedComponent(description: String, vararg components: String): Boolean {
    if (isAnySelectedComponent(*components)) {
        return false
    }
    println("      ✓ $description intentionally excluded because none of these components are selected: ${components.joinToString(", ")}")
    return true
}

internal fun testRunnerRuntimeProjectName(): String =
    System.getenv("TEST_RUNNER_RUNTIME_PROJECT_NAME").orEmpty()
        .ifBlank { System.getenv("RUNTIME_PROJECT_NAME").orEmpty() }
        .ifBlank { "webservices" }

internal fun runtimeServiceContainerName(serviceName: String): String {
    val project = testRunnerRuntimeProjectName()
    return if (project.startsWith("webservices_testdev_")) {
        "$project-$serviceName-1"
    } else {
        serviceName
    }
}
