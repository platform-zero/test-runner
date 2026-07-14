package org.webservices.testrunner.framework

fun isolatedDockerHostFromEnv(): String {
    val explicitHost = System.getenv("ISOLATED_DOCKER_HOST")

    if (!explicitHost.isNullOrBlank()) {
        return explicitHost
    }

    val runtime = System.getenv("TEST_RUNNER_CONTAINER_CLI")?.trim().orEmpty().ifBlank { "podman" }
    if (runtime == "podman") {
        return System.getenv("CONTAINER_HOST")?.takeIf { it.isNotBlank() } ?: "unix:///run/podman/podman.sock"
    }

    val dockerHost = System.getenv("DOCKER_HOST")
    val lowerDockerHost = dockerHost.orEmpty().lowercase()
    if (!dockerHost.isNullOrBlank()) {
        return dockerHost
    }

    return ""
}
