package org.webservices.testrunner.framework

data class ContainerCommandResult(
    val exitCode: Int,
    val output: String
)

object ContainerCli {
    private const val CONTAINER_CLI_ENV = "TEST_RUNNER_CONTAINER_CLI"
    private const val DEFAULT_PODMAN_HOST = "unix:///run/podman/podman.sock"
    private const val ALLOW_LOCAL_MUTATING_FALLBACK_ENV = "TEST_RUNNER_ALLOW_LOCAL_CONTAINER_MUTATIONS"

    fun run(vararg args: String): ContainerCommandResult {
        val runtime = containerCli()
        if (runtime == "podman") {
            return runWithContainerCli(args.toList(), runtime, System.getenv("CONTAINER_HOST") ?: DEFAULT_PODMAN_HOST)
        }

        return if (!isMutatingCommand(args.toList()) || allowLocalMutatingFallback()) {
            runWithContainerCli(args.toList(), runtime, null)
        } else {
            ContainerCommandResult(
                exitCode = 2,
                output = "Refusing mutating container command without explicit isolated runtime host or $ALLOW_LOCAL_MUTATING_FALLBACK_ENV"
            )
        }
    }

    private fun containerCli(): String =
        System.getenv(CONTAINER_CLI_ENV)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "podman"

    private fun allowLocalMutatingFallback(): Boolean =
        when (System.getenv(ALLOW_LOCAL_MUTATING_FALLBACK_ENV)?.trim()?.lowercase()) {
            "1", "true", "yes", "on" -> true
            else -> false
        }

    internal fun isMutatingCommand(args: List<String>): Boolean {
        if (args.isEmpty()) return false
        val top = args.first().lowercase()
        if (top in setOf("build", "run", "start", "stop", "restart", "kill", "rm", "rmi", "pull", "push", "tag")) {
            return true
        }
        if (top == "compose") {
            val runtimeCommands = args.drop(1)
                .map { it.lowercase() }
                .filterNot { it.startsWith("-") }
            return runtimeCommands.any {
                it in setOf("up", "down", "start", "stop", "restart", "kill", "rm", "run", "exec", "build", "pull", "push")
            }
        }
        if (top in setOf("container", "image", "network", "volume", "system")) {
            val subCommands = args.drop(1)
                .map { it.lowercase() }
                .filterNot { it.startsWith("-") }
            val subCommand = subCommands.firstOrNull() ?: return false
            return when (top) {
                "container" -> subCommand in setOf("create", "run", "start", "stop", "restart", "kill", "rm", "prune", "exec")
                "image" -> subCommand in setOf("build", "pull", "push", "rm", "prune", "tag", "import", "load")
                "network" -> subCommand in setOf("create", "connect", "disconnect", "rm", "prune")
                "volume" -> subCommand in setOf("create", "rm", "prune")
                "system" -> subCommand == "prune"
                else -> false
            }
        }
        return false
    }

    private fun runWithContainerCli(args: List<String>, runtime: String, host: String?): ContainerCommandResult {
        val usePodmanRemote = runtime == "podman" && !host.isNullOrBlank()
        val command = listOf(runtime) + (if (usePodmanRemote) listOf("--remote") else emptyList()) + args
        val processBuilder = ProcessBuilder(command).redirectErrorStream(true)
        if (runtime == "podman" && !host.isNullOrBlank()) {
            processBuilder.environment()["CONTAINER_HOST"] = host
        }
        return try {
            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            ContainerCommandResult(exitCode = exitCode, output = output)
        } catch (error: java.io.IOException) {
            ContainerCommandResult(
                exitCode = 127,
                output = "Unable to execute container CLI '$runtime': ${error.message.orEmpty()}"
            )
        }
    }
}
