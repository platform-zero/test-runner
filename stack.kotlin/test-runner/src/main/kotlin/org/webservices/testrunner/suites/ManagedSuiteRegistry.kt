package org.webservices.testrunner.suites

import org.webservices.testrunner.framework.TestRunner

internal enum class ManagedSuiteCategory {
    CORE,
    AUTH,
    APPS,
    LIVE_INGESTION,
}

internal data class ManagedSuiteRegistration(
    val id: String,
    val category: ManagedSuiteCategory,
    val requiredComponents: Set<String> = emptySet(),
    val run: suspend TestRunner.() -> Unit,
)

/**
 * The single inventory of deploy-safe managed Kotlin leaf suites.
 *
 * Optional suites are omitted before test registration when their component is
 * not selected. They are therefore reported as not applicable rather than as
 * skipped blocking tests.
 */
internal val managedSuiteRegistry: List<ManagedSuiteRegistration> = listOf(
    ManagedSuiteRegistration("foundation", ManagedSuiteCategory.CORE) { foundationTests() },
    ManagedSuiteRegistration("infrastructure", ManagedSuiteCategory.CORE) { infrastructureTests() },
    ManagedSuiteRegistration("postgres-database", ManagedSuiteCategory.CORE, setOf("postgres")) { postgresDatabaseTests() },
    ManagedSuiteRegistration("mariadb-database", ManagedSuiteCategory.CORE, setOf("mariadb")) { mariaDbDatabaseTests() },
    ManagedSuiteRegistration("valkey-cache", ManagedSuiteCategory.CORE, setOf("valkey")) { valkeyCachingLayerTests() },
    ManagedSuiteRegistration("memcached-cache", ManagedSuiteCategory.CORE, setOf("memcached")) { memcachedCachingLayerTests() },

    ManagedSuiteRegistration("authentication", ManagedSuiteCategory.AUTH) { authenticationTests() },
    ManagedSuiteRegistration("authenticated-operations", ManagedSuiteCategory.AUTH) { authenticatedOperationsTests() },
    ManagedSuiteRegistration("enhanced-authentication", ManagedSuiteCategory.AUTH) { enhancedAuthenticationTests() },

    ManagedSuiteRegistration("bookstack", ManagedSuiteCategory.APPS, setOf("bookstack")) { bookStackProductivityTests() },
    ManagedSuiteRegistration("forgejo", ManagedSuiteCategory.APPS, setOf("forgejo")) { forgejoProductivityTests() },
    ManagedSuiteRegistration("planka", ManagedSuiteCategory.APPS, setOf("planka")) { plankaProductivityTests() },
    ManagedSuiteRegistration("email-stack", ManagedSuiteCategory.APPS, setOf("mailserver")) { emailStackTests() },
    ManagedSuiteRegistration("synapse", ManagedSuiteCategory.APPS, setOf("matrix")) { synapseCommunicationTests() },
    ManagedSuiteRegistration("element", ManagedSuiteCategory.APPS, setOf("matrix")) { elementCommunicationTests() },
    ManagedSuiteRegistration("mastodon", ManagedSuiteCategory.APPS, setOf("mastodon")) { mastodonCommunicationTests() },
    ManagedSuiteRegistration("mastodon-collaboration", ManagedSuiteCategory.APPS, setOf("mastodon")) { collaborationTests() },
    ManagedSuiteRegistration("mastodon-extended", ManagedSuiteCategory.APPS, setOf("mastodon")) { mastodonExtendedCommunicationTests() },
    ManagedSuiteRegistration("prometheus", ManagedSuiteCategory.APPS, setOf("observability")) { prometheusMonitoringTests() },
    ManagedSuiteRegistration("grafana", ManagedSuiteCategory.APPS, setOf("observability")) { grafanaMonitoringTests() },
    ManagedSuiteRegistration("node-exporter", ManagedSuiteCategory.APPS, setOf("observability")) { nodeExporterMonitoringTests() },
    ManagedSuiteRegistration("alertmanager", ManagedSuiteCategory.APPS, setOf("observability")) { alertmanagerMonitoringTests() },
    ManagedSuiteRegistration("ntfy-utility", ManagedSuiteCategory.APPS, setOf("ntfy")) { ntfyUtilityServicesTests() },
    ManagedSuiteRegistration("ntfy-extended", ManagedSuiteCategory.APPS, setOf("ntfy")) { ntfyExtendedCommunicationTests() },
    ManagedSuiteRegistration("onlyoffice-files", ManagedSuiteCategory.APPS, setOf("onlyoffice")) { onlyofficeFileManagementTests() },
    ManagedSuiteRegistration("onlyoffice-extended", ManagedSuiteCategory.APPS, setOf("onlyoffice")) { onlyofficeExtendedProductivityTests() },
    ManagedSuiteRegistration("seafile", ManagedSuiteCategory.APPS, setOf("seafile")) { seafileFileManagementTests() },
    ManagedSuiteRegistration("portal", ManagedSuiteCategory.APPS, setOf("portal")) { portalUtilityServicesTests() },
    ManagedSuiteRegistration("homeassistant", ManagedSuiteCategory.APPS, setOf("homeassistant")) { homeAssistantTests() },
    ManagedSuiteRegistration("jupyter-notebook", ManagedSuiteCategory.APPS, setOf("jupyterhub")) { jupyterNotebookProductivityTests() },
    ManagedSuiteRegistration("jupyterhub", ManagedSuiteCategory.APPS, setOf("jupyterhub")) { jupyterHubExtendedProductivityTests() },
    ManagedSuiteRegistration("jupyterhub-ui", ManagedSuiteCategory.APPS, setOf("jupyterhub")) { userInterfaceTests() },
    ManagedSuiteRegistration("kopia", ManagedSuiteCategory.APPS, setOf("kopia")) { backupTests() },
    ManagedSuiteRegistration("vaultwarden-security", ManagedSuiteCategory.APPS, setOf("vaultwarden")) { securityTests() },

    ManagedSuiteRegistration("opensearch-retrieval", ManagedSuiteCategory.LIVE_INGESTION, setOf("search")) { searchServiceTests() },
    ManagedSuiteRegistration("bookstack-publication", ManagedSuiteCategory.LIVE_INGESTION, setOf("pipeline", "bookstack")) { bookStackIntegrationTests() },
    ManagedSuiteRegistration("jupyter-pipeline", ManagedSuiteCategory.LIVE_INGESTION, setOf("jupyterhub", "pipeline")) { jupyterPipelineIntegrationTests() },
)

internal suspend fun TestRunner.runManagedCategory(category: ManagedSuiteCategory) {
    managedSuiteRegistry
        .asSequence()
        .filter { it.category == category }
        .filter { registration ->
            registration.requiredComponents.isEmpty() ||
                registration.requiredComponents.all(::isSelectedComponent)
        }
        .forEach { registration -> registration.run.invoke(this) }
}
