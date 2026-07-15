package org.webservices.testrunner

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JupyterAndTunnelHardeningConfigTest {

    @Test
    fun `notebook startup writes env with restrictive permissions`() {
        val startupScript = repoFileText("stack.containers/jupyter-notebook/startup-config.sh")

        assertTrue(startupScript.contains("old_umask = os.umask(0o077)"))
        assertTrue(startupScript.contains("env_file.chmod(0o600)"))
        assertTrue(startupScript.contains("jupyter_config_dir.chmod(0o700)"))
        assertTrue(startupScript.contains("os.umask(old_umask)"))
    }

    fun `jupyterhub does not inject shared production secrets into user notebooks`() {
        val runtime = repoFileText("stack.runtime.yaml")
        val config = repoFileText("stack.config/jupyterhub/jupyterhub_config.py")
        val startup = repoFileText("stack.containers/jupyter-notebook/startup-config.sh")

        assertFalse(runtime.contains("OPENAI_API_KEY:"))
        assertFalse(runtime.contains("POSTGRES_PASSWORD: ${'$'}{POSTGRES_PIPELINE_PASSWORD}"))
        assertFalse(config.contains("'OPENAI_API_KEY':"))
        assertFalse(config.contains("'POSTGRES_USER': os.environ.get('POSTGRES_USER', 'pipeline_user')"))
        assertFalse(startup.contains("'OPENAI_API_KEY':"))
        assertFalse(startup.contains("print(env_path.read_text()"))
    }

    private fun repoFileText(relativePath: String): String =
        Files.readString(repoRoot().resolve(relativePath))

    private fun repoRoot(): Path {
        var current = Path.of("").toAbsolutePath()
        repeat(8) {
            if (Files.exists(current.resolve("MODULE.bazel"))) {
                return current
            }
            current = current.parent ?: return@repeat
        }
        error("Could not locate repository root from ${Path.of("").toAbsolutePath()}")
    }
}
