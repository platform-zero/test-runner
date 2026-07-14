package org.webservices.testrunner.framework

fun isolatedContainerHostFromEnv(): String {
    val explicitHost = System.getenv("ISOLATED_CONTAINER_HOST")

    if (!explicitHost.isNullOrBlank()) {
        return explicitHost
    }

    val runtime = System.getenv("TEST_RUNNER_CONTAINER_CLI")?.trim().orEmpty().ifBlank { "podman" }
    if (runtime == "podman") {
        return System.getenv("CONTAINER_HOST")?.takeIf { it.isNotBlank() } ?: "unix:///run/podman/podman.sock"
    }

    return ""
}
