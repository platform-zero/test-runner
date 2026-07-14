package org.webservices.testrunner.suites

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

internal fun isTestdevProfile(): Boolean =
    System.getenv("TESTDEV_SKIP_GPU_INGESTION") == "1"

private val selectedComponentNames: Set<String>? by lazy {
    val candidates = listOfNotNull(
        System.getenv("TEST_RUNNER_COMPONENTS_LOCK_FILE"),
        System.getenv("WEBSERVICES_COMPONENTS_LOCK_FILE"),
        "/component-lock/components.lock.json",
        "/runtime/components.lock.json",
        "/app/build/site/components.lock.json",
        "/app/site/components.lock.json",
    ).filter { it.isNotBlank() }

    val lockFile = candidates
        .map(::File)
        .firstOrNull { it.isFile && it.canRead() }
        ?: return@lazy null

    runCatching {
        Json.parseToJsonElement(lockFile.readText())
            .jsonObject["components"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet()
    }.getOrNull()
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
