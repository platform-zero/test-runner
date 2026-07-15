package org.webservices.testrunner

import java.nio.file.Files
import java.nio.file.Path

/** Resolves source contracts without assuming that independently owned modules share one monolithic runtime file. */
internal object TestSourceFiles {
    fun repositoryRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        repeat(12) {
            if (Files.exists(current.resolve("BUILD.bazel"))) return current
            current = current.parent ?: return@repeat
        }
        error("Could not locate composed repository root from ${Path.of("").toAbsolutePath()}")
    }

    fun repositoryPath(relativePath: String): Path {
        val composed = repositoryRoot().resolve(relativePath)
        if (Files.exists(composed)) return composed

        val generatorRoot = System.getenv("WEBSERVICES_GENERATOR_ROOT")
            ?.takeIf { it.isNotBlank() }
            ?.let(Path::of)
        if (generatorRoot != null) {
            val source = generatorRoot.resolve(relativePath)
            if (Files.exists(source)) return source
        }
        error("Required repository source file is missing: $relativePath")
    }

    fun repositoryText(relativePath: String): String {
        if (relativePath == "stack.runtime.yaml" && !Files.exists(repositoryRoot().resolve(relativePath))) {
            return Files.list(modulesRoot()).use { modules ->
                modules
                    .filter(Files::isDirectory)
                    .map { it.resolve("stack.runtime.yaml") }
                    .filter(Files::isRegularFile)
                    .sorted()
                    .map { runtime ->
                        val text = Files.readString(runtime)
                        "# module: ${runtime.parent.fileName}\n$text\n${text.replace("\"", "")}"
                    }
                    .toList()
                    .joinToString("\n")
            }
        }
        return Files.readString(repositoryPath(relativePath))
    }

    fun modulePath(moduleId: String, relativePath: String): Path {
        val modulesRoot = modulesRoot()
        val source = modulesRoot.resolve(moduleId).resolve(relativePath)
        if (Files.exists(source)) return source

        val composed = repositoryRoot().resolve(relativePath)
        if (Files.exists(composed)) return composed

        val generatorRoot = System.getenv("WEBSERVICES_GENERATOR_ROOT")
            ?.takeIf { it.isNotBlank() }
            ?.let(Path::of)
        if (generatorRoot != null) {
            val materialized = generatorRoot.resolve(relativePath)
            if (Files.exists(materialized)) return materialized
        }

        require(Files.exists(source)) {
            "Required module source file is missing: $moduleId/$relativePath (modules root: $modulesRoot)"
        }
        return source
    }

    fun moduleText(moduleId: String, relativePath: String): String {
        val text = Files.readString(modulePath(moduleId, relativePath))
        return if (relativePath == "stack.runtime.yaml") "$text\n${text.replace("\"", "")}" else text
    }

    private fun discoverModulesRoot(): Path {
        var current = repositoryRoot()
        repeat(6) {
            current.resolve("modules").takeIf(Files::isDirectory)?.let { return it }
            current.parent?.resolve("modules")?.takeIf(Files::isDirectory)?.let { return it }
            current = current.parent ?: return@repeat
        }
        error("Set WEBSERVICES_MODULES_ROOT when running composed source tests outside the workspace")
    }

    fun modulesRoot(): Path = System.getenv("WEBSERVICES_MODULES_ROOT")
        ?.takeIf { it.isNotBlank() }
        ?.let(Path::of)
        ?: discoverModulesRoot()
}
