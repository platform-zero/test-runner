package org.webservices.testrunner.framework

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContainerCliTest {
    @Test
    fun `detects top level mutating docker commands`() {
        assertTrue(ContainerCli.isMutatingCommand(listOf("run", "--rm", "busybox", "echo", "ok")))
        assertTrue(ContainerCli.isMutatingCommand(listOf("rm", "-f", "container-id")))
        assertTrue(ContainerCli.isMutatingCommand(listOf("build", "-t", "stack/test", ".")))
    }

    @Test
    fun `detects compose mutating commands and ignores read only compose queries`() {
        assertTrue(ContainerCli.isMutatingCommand(listOf("compose", "up", "-d")))
        assertTrue(ContainerCli.isMutatingCommand(listOf("compose", "--project-name", "webservices", "exec", "app", "sh")))
        assertFalse(ContainerCli.isMutatingCommand(listOf("compose", "ps")))
        assertFalse(ContainerCli.isMutatingCommand(listOf("compose", "logs", "--tail", "20", "app")))
    }

    @Test
    fun `treats inspect and info commands as non mutating`() {
        assertFalse(ContainerCli.isMutatingCommand(listOf("info")))
        assertFalse(ContainerCli.isMutatingCommand(listOf("inspect", "container-id")))
        assertFalse(ContainerCli.isMutatingCommand(listOf("container", "inspect", "container-id")))
        assertFalse(ContainerCli.isMutatingCommand(listOf("network", "ls")))
    }
}
