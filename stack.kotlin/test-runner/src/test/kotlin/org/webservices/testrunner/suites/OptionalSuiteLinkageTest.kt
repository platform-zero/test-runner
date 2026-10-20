package org.webservices.testrunner.suites

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.webservices.testrunner.framework.ServiceClient
import org.webservices.testrunner.framework.TestEnvironment
import org.webservices.testrunner.framework.TestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Actual suspend linkage with local code-only probes; no HTTP, IdP or finance. */
object OptionalLinkageProbes {
    var calls = 0
    @JvmStatic suspend fun immediate(runner: TestRunner) { check(runner.environment == TestEnvironment.Localhost); calls++ }
    @JvmStatic suspend fun suspended(runner: TestRunner) { delay(1); immediate(runner) }
    @JvmStatic suspend fun broken(runner: TestRunner): Unit { check(runner.environment == TestEnvironment.Localhost); error("optional fixture failure") }
}

class OptionalSuiteLinkageTest {
    private fun withRunner(block: suspend (TestRunner) -> Unit) = runBlocking {
        val http = HttpClient(MockEngine { error("source linkage probes must not make HTTP requests") })
        val environment = TestEnvironment.Localhost
        try { block(TestRunner(environment, ServiceClient(environment.endpoints, http), http)) }
        finally { http.close() }
    }

    @Test fun `present immediate and genuinely suspended implementations execute`() = withRunner { runner ->
        OptionalLinkageProbes.calls = 0
        runner.invokeOptionalModuleSuite(OptionalLinkageProbes::class.java.name, "immediate")
        runner.invokeOptionalModuleSuite(OptionalLinkageProbes::class.java.name, "suspended")
        assertEquals(2, OptionalLinkageProbes.calls)
    }

    @Test fun `implementation error is not swallowed or reflection wrapped`() = withRunner { runner ->
        val failure = assertFailsWith<IllegalStateException> {
            runner.invokeOptionalModuleSuite(OptionalLinkageProbes::class.java.name, "broken")
        }
        assertEquals("optional fixture failure", failure.message)
    }

    @Test fun `selected absent implementation is a failure not a skip`() = withRunner { runner ->
        assertFailsWith<ClassNotFoundException> { runner.invokeOptionalModuleSuite("absent.module.SelectedSuite", "absent") }
    }

    @Test fun `selected absent method is a failure not a skip`() = withRunner { runner ->
        assertFailsWith<NoSuchMethodException> { runner.invokeOptionalModuleSuite(OptionalLinkageProbes::class.java.name, "absent") }
    }
}
