package org.webservices.testrunner.framework

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContainerCliTest {
    @Test
    fun `detects top level mutating container commands`() {
        assertTrue(ContainerCli.isMutatingCommand(listOf("run", "--rm", "busybox", "echo", "ok")))
        assertTrue(ContainerCli.isMutatingCommand(listOf("rm", "-f", "container-id")))
        assertTrue(ContainerCli.isMutatingCommand(listOf("build", "-t", "stack/test", ".")))
    }

    @Test
    fun `detects namespaced podman mutating commands`() {
        assertTrue(ContainerCli.isMutatingCommand(listOf("container", "exec", "app", "sh")))
        assertTrue(ContainerCli.isMutatingCommand(listOf("network", "connect", "webservices_default", "app")))
        assertTrue(ContainerCli.isMutatingCommand(listOf("volume", "rm", "stale-volume")))
        assertFalse(ContainerCli.isMutatingCommand(listOf("network", "ls")))
    }

    @Test
    fun `treats inspect and info commands as non mutating`() {
        assertFalse(ContainerCli.isMutatingCommand(listOf("info")))
        assertFalse(ContainerCli.isMutatingCommand(listOf("inspect", "container-id")))
        assertFalse(ContainerCli.isMutatingCommand(listOf("container", "inspect", "container-id")))
        assertFalse(ContainerCli.isMutatingCommand(listOf("network", "ls")))
    }
}
