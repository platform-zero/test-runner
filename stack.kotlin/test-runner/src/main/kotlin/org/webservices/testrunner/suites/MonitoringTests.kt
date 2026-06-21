package org.webservices.testrunner.suites

import org.webservices.testrunner.framework.TestRunner

suspend fun TestRunner.monitoringTests() {
    prometheusMonitoringTests()
    grafanaMonitoringTests()
    nodeExporterMonitoringTests()
    cadvisorMonitoringTests()
    dozzleMonitoringTests()
    alertmanagerMonitoringTests()
}
