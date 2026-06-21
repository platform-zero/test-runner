package org.webservices.testrunner.suites

import org.webservices.testrunner.framework.TestRunner

suspend fun TestRunner.utilityServicesTests() {
    portalUtilityServicesTests()
    ntfyUtilityServicesTests()
}
