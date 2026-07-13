package org.webservices.testrunner.framework

fun isolatedDockerHostFromEnv(): String {
    val explicitHost = System.getenv("ISOLATED_DOCKER_HOST")

    if (!explicitHost.isNullOrBlank()) {
        return explicitHost
    }

    if (System.getenv("TEST_RUNNER_CONTAINER_CLI")?.trim() == "podman") {
        return System.getenv("CONTAINER_HOST").orEmpty()
    }

    val dockerHost = System.getenv("DOCKER_HOST")
    val lowerDockerHost = dockerHost.orEmpty().lowercase()
    val hostProxy = lowerDockerHost.contains("docker-socket-proxy") ||
        lowerDockerHost.contains("docker-socket-controller-proxy") ||
        lowerDockerHost.contains("docker-proxy:2375")
    if (!dockerHost.isNullOrBlank() && !hostProxy) {
        return dockerHost
    }

    return "tcp://docker-socket-controller-proxy:2375"
}
