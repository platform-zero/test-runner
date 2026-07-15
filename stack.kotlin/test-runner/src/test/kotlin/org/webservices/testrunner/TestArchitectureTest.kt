package org.webservices.testrunner

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.notExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestArchitectureTest {

    @Test
    fun `main exposes the repo owned Kotlin suites only`() {
        val text = Files.readString(repoRoot().resolve("stack.kotlin/test-runner/src/main/kotlin/org/webservices/testrunner/Main.kt"))
        val catalogText = Files.readString(repoRoot().resolve("stack.kotlin/test-runner/src/main/kotlin/org/webservices/testrunner/SuiteCatalog.kt"))
        val suitesText = Files.readString(repoRoot().resolve("stack.kotlin/test-runner/src/main/kotlin/org/webservices/testrunner/suites/WebServicesTests.kt"))

        assertTrue(text.contains("SuiteCatalog.resolve"))
        assertTrue(catalogText.contains("stackContractTests()"))
        assertTrue(catalogText.contains("agentLabTests()"))
        assertTrue(suitesText.contains("suspend fun TestRunner.stackLiveIngestionTests()"))
        assertFalse(catalogText.contains("tradingTests()"))
        assertFalse(catalogText.contains("web3WalletTests()"))
    }

    @Test
    fun `run tests script launches deployed tests through rootless podman`() {
        val text = Files.readString(repoRoot().resolve("stack.containers/test-runner/run-tests.sh"))
        val runtimeText = Files.readString(repoRoot().resolve("stack.runtime.yaml"))

        assertTrue(text.contains("TEST_RUNNER_SERVICE=\"test-runner\""))
        assertTrue(text.contains("DEFAULT_KT_SUITE=\"\${DEFAULT_KT_SUITE:-stack-contract}\""))
        assertTrue(text.contains("kt-contract"))
        assertTrue(text.contains("kt-live-ingestion"))
        assertTrue(text.contains("kt-recovery"))
        assertTrue(text.contains("kt-agent-lab"))
        assertTrue(text.contains("WEBSERVICES_ROOTLESS_USER=\"\${WEBSERVICES_ROOTLESS_USER:-webservices}\""))
        assertTrue(text.contains("rootless_podman()"))
        assertTrue(text.contains("podman_run_network_args"))
        assertTrue(text.contains("run_podman_test_container"))
        assertTrue(text.contains("CADDY_URL=\${CADDY_URL:-http://host.containers.internal:80}"))
        assertTrue(text.contains("PLAYWRIGHT_IGNORE_HTTPS_ERRORS=\${PLAYWRIGHT_IGNORE_HTTPS_ERRORS:-true}"))
        assertTrue(text.contains("doctor"))
        assertFalse(text.contains("TEST_RUNNER_HOST_XDG_RUNTIME_DIR"))
        assertFalse(text.contains("CONTAINER_HOST=\"unix:///run/podman/podman.sock\""))
        assertTrue(runtimeText.contains("TEST_RUNNER_RUNTIME_ROOT: /runtime"))
        assertTrue(runtimeText.contains("CADDY_URL: http://host.containers.internal:80"))
        assertFalse(runtimeText.contains("AUTHELIA_API_URL"))
        assertTrue(runtimeText.contains("IDENTITY_PROVIDER: \${IDENTITY_PROVIDER:-keycloak}"))
        assertTrue(runtimeText.contains("KEYCLOAK_INTERNAL_URL: \${KEYCLOAK_INTERNAL_URL:-http://keycloak:8080}"))
        assertTrue(runtimeText.contains("KEYCLOAK_ADMIN_PASSWORD: \${KEYCLOAK_ADMIN_PASSWORD}"))
        assertTrue(runtimeText.contains("OPENSEARCH_URL: \${OPENSEARCH_URL:-http://opensearch:9200}"))
        assertTrue(runtimeText.contains("OPENSEARCH_PASSWORD: \${OPENSEARCH_ADMIN_PASSWORD}"))
        assertTrue(runtimeText.contains("INFERENCE_CONTROLLER_API_TOKEN: \${INFERENCE_CONTROLLER_API_TOKEN}"))
        assertTrue(runtimeText.contains("MODEL_CONTEXT_OIDC_REDIRECT_URI: \${MODEL_CONTEXT_OIDC_REDIRECT_URI:-http://test-runner-managed/callback}"))
        assertTrue(runtimeText.contains("TEST_RUNNER_CONTAINER_CLI: podman"))
        assertTrue(runtimeText.contains("CONTAINER_HOST: unix:///run/podman/podman.sock"))
        assertFalse(runtimeText.contains("\${TEST_RESULTS_HOST_DIR:-./test-results}"))
        assertFalse(runtimeText.contains("socket-controller-proxy"))
        assertFalse(runtimeText.contains("container-controller"))
        assertFalse(text.contains("suite_service()"))
        assertFalse(text.contains("test-playwright-e2e"))
    }

    @Test
    fun `podman runtime does not expose broad container socket proxy services`() {
        val repoRoot = repoRoot()
        val agentWorkspaceSuites = Files.readString(repoRoot.resolve("stack.kotlin/test-runner/src/main/kotlin/org/webservices/testrunner/suites/AgentWorkspaceSuites.kt"))
        assertFalse(agentWorkspaceSuites.contains("/var/run/podman/podman.sock:/var/run/podman/podman.sock"))
    }

    @Test
    fun `security sensitive runtime boundaries are explicit`() {
        val repoRoot = repoRoot()
        val caddyRuntime = Files.readString(repoRoot.resolve("stack.runtime.yaml"))
        val caddyfile = Files.readString(repoRoot.resolve("stack.config/caddy/Caddyfile"))
        val renderValues = Files.readString(repoRoot.resolve("scripts/lib/render-values.sh"))

        assertTrue(caddyRuntime.contains("BOOKSTACK_INTERNAL_API_TOKEN: \${BOOKSTACK_INTERNAL_API_TOKEN}"))
        assertTrue(caddyRuntime.contains("OPENSEARCH_BASIC_AUTH: \${OPENSEARCH_BASIC_AUTH}"))
        assertTrue(caddyRuntime.contains("HOMEASSISTANT_TRUSTED_PROXY_SECRET: \${HOMEASSISTANT_TRUSTED_PROXY_SECRET}"))
        assertTrue(caddyRuntime.contains("ONBOARDING_TRUSTED_PROXY_SECRET: \${ONBOARDING_TRUSTED_PROXY_SECRET}"))
        assertTrue(caddyfile.contains("header X-Internal-Token {\$BOOKSTACK_INTERNAL_API_TOKEN}"))
        assertTrue(caddyfile.contains("header_up X-Trusted-Proxy-Secret {\$HOMEASSISTANT_TRUSTED_PROXY_SECRET}"))
        assertTrue(caddyfile.contains("header_up X-Trusted-Proxy-Secret {\$ONBOARDING_TRUSTED_PROXY_SECRET}"))
        assertFalse(caddyfile.contains("172.20.0.0/24 172.21.0.0/24 172.22.0.0/24"))
        assertTrue(caddyfile.contains("header_up Authorization \"Basic {\$OPENSEARCH_BASIC_AUTH}\""))
        assertTrue(caddyfile.contains("request_header -Authorization"))
        assertTrue(renderValues.contains("derive_stack_secret airflow-webserver 64"))
        assertTrue(renderValues.contains("derive_stack_secret bookstack-internal-api 64"))
        assertTrue(renderValues.contains("derive_stack_secret homeassistant-trusted-proxy 64"))
    }

    @Test
    fun `mariadb supports internal bootstrap root connections`() {
        val mariadbText = Files.readString(repoRoot().resolve("stack.runtime.yaml"))

        assertTrue(mariadbText.contains("MARIADB_ROOT_HOST: \"%\""))
        assertTrue(mariadbText.contains("MARIADB_ROOT_PASSWORD: \${MARIADB_ADMIN_PASSWORD}"))
        assertTrue(mariadbText.contains("mariadb --protocol=TCP -h 127.0.0.1"))
        assertFalse(mariadbText.contains("healthcheck.sh --connect"))
    }

    @Test
    fun `shipped deploy script is bundle local and renders runtime in user tmpfs`() {
        val text = Files.readString(repoRoot().resolve("scripts/deploy.sh"))
        val buildText = Files.readString(repoRoot().resolve("build.sh"))
        val runtimeStateText = Files.readString(repoRoot().resolve("scripts/lib/runtime-state.sh"))
        val verifyText = Files.readString(repoRoot().resolve("scripts/verify.sh"))

        assertTrue(runtimeStateText.contains("webservices-runtime"))
        assertTrue(text.contains("site_manifest_path"))
        assertTrue(text.contains("runtime/stack.env"))
        assertTrue(text.contains("run_compose_from_bundle"))
        assertTrue(text.contains("run_model_prep_jobs"))
        assertTrue(buildText.contains("render-systemd-user.sh"))
        assertTrue(text.contains("install-systemd-user-units.sh"))
        assertTrue(text.contains("missing pre-rendered systemd user units"))
        assertTrue(text.contains("reconcile_target"))
        assertTrue(text.contains("wait_for_target_reconcile"))
        assertTrue(text.contains("aux_action=\"start\""))
        assertTrue(text.contains("user_systemd_list_matching_jobs_raw"))
        assertTrue(text.contains("restart_post_reconcile_units"))
        assertTrue(text.contains("webservices-keycloak-configure.service"))
        assertTrue(text.contains("webservices-keycloak-auth-gateway.service"))
        assertTrue(verifyText.contains("default_test_results_host_dir"))
        assertFalse(verifyText.contains("\$DEPLOY_ROOT/test-results"))
        assertFalse(text.contains("webservices-next"))
        assertFalse(text.contains("webservices-releases"))
        assertFalse(text.contains("ln -sfnT"))
        assertFalse(text.contains("render-systemd-user.sh"))
        assertFalse(text.contains("post-reconcile unit inventory"))
        assertFalse(text.contains("siteConfigRoot"))
    }

    @Test
    fun `scripts directory only exposes the current bundled command surface`() {
        val topLevelFiles = Files.list(repoRoot().resolve("scripts")).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .map { it.fileName.toString() }
                .sorted()
                .toList()
        }

        assertEquals(
            listOf(
                "build-artifact.sh",
                "deploy.sh",
                "generate-contract-reports.sh",
                "init-site.sh",
                "mount-diagnostics.sh",
                "security-audit.sh",
                "stackctl.sh",
                "test-component-selection.sh",
                "test-contract-reports.sh",
                "test-deploy-scope.sh",
                "test-deploy-state.sh",
                "test-docs.sh",
                "test-env-file-security.sh",
                "test-external-modules.sh",
                "test-jellyfin-ffmpeg-websafe.sh",
                "test-mount-diagnostics.sh",
                "test-service-contracts.sh",
                "verify.sh",
            ),
            topLevelFiles,
        )
    }

    @Test
    fun `repo root only exposes build as public local deployment command`() {
        val repoRoot = repoRoot()

        assertTrue(Files.exists(repoRoot.resolve("build.sh")))
        assertFalse(Files.exists(repoRoot.resolve("sync.sh")))
        assertFalse(Files.exists(repoRoot.resolve("deploy.sh")))
        assertFalse(Files.exists(repoRoot.resolve("render.sh")))
        assertFalse(Files.exists(repoRoot.resolve("sync-dist.sh")))
        assertFalse(Files.exists(repoRoot.resolve("test.sh")))
        assertFalse(Files.exists(repoRoot.resolve("wait-ready.sh")))
    }

    @Test
    fun `build requires explicit site manifest and no repo site-config link exists`() {
        val repoRoot = repoRoot()
        val buildText = Files.readString(repoRoot.resolve("build.sh"))
        val manifestLib = repoRoot.resolve("scripts/lib/site-manifest.sh")

        assertTrue(buildText.contains("--manifest <path-to-manifest.json>"))
        assertTrue(buildText.contains("site_manifest_path"))
        assertTrue(buildText.contains("mkdir -p \"\$DIST_DIR/build\""))
        assertTrue(buildText.contains("generate-contract-reports.sh"))
        assertFalse(buildText.contains("TEST_RESULTS_HOST_DIR=\"\${TEST_RESULTS_HOST_DIR:-\$SCRIPT_DIR/test-results}\""))
        assertTrue(Files.exists(manifestLib))
        assertTrue(repoRoot.resolve("scripts/lib/site-config.sh").notExists())
        assertTrue(repoRoot.resolve("site-config").notExists())
        assertFalse(buildText.contains("--site <site>"))
        assertFalse(buildText.contains("--url <public-site-url>"))
    }

    @Test
    fun `deploy recreates top-level reports mount for portal after rsync delete`() {
        val deployText = Files.readString(repoRoot().resolve("scripts/deploy.sh"))
        val portalComposeText = Files.readString(repoRoot().resolve("stack.runtime.yaml"))

        assertTrue(portalComposeText.contains("./reports:/contracts/reports:ro"))
        assertTrue(deployText.contains("ensure_generated_report_link"))
        assertTrue(deployText.contains("local reports_source=\"\$BUNDLE_ROOT/reports\""))
        assertTrue(deployText.contains("local reports_link=\"\$DEPLOY_ROOT/reports\""))
        assertTrue(deployText.contains("ln -s \"\$reports_source\" \"\$reports_link\""))
        assertTrue(deployText.contains("ensure_generated_report_link\n\nset_phase \"install-systemd-units\""))
    }

    @Test
    fun `test runner image uses the stable playwright uid for host bind mounts`() {
        val containerfileText = Files.readString(repoRoot().resolve("stack.containers/test-runner/Containerfile"))
        val entrypointText = Files.readString(repoRoot().resolve("stack.containers/test-runner/container-entrypoint.sh"))

        assertTrue(containerfileText.contains("usermod -aG root pwuser"))
        assertTrue(containerfileText.contains("USER pwuser"))
        assertTrue(containerfileText.contains("COPY stack.containers/test-runner/fixtures/aider-runtime /app/stack.containers/test-runner/fixtures/aider-runtime"))
        assertFalse(containerfileText.contains("COPY stack.containers/test-runner/fixtures/codex-runtime /app/stack.containers/test-runner/fixtures/codex-runtime"))
        assertFalse(containerfileText.contains("testing_container_user"))
        assertTrue(entrypointText.contains("TEST_USER=\"pwuser\""))
    }

    @Test
    fun `managed runner auth defaults to keycloak without authelia api wiring`() {
        val testRunnerRuntime = Files.readString(repoRoot().resolve("stack.runtime.yaml"))
        val authGatewayRuntime = Files.readString(repoRoot().resolve("stack.runtime.yaml"))
        val realmTemplate = Files.readString(repoRoot().resolve("stack.config/keycloak/realm/webservices-realm.json.template"))

        assertTrue(testRunnerRuntime.contains("IDENTITY_PROVIDER: \${IDENTITY_PROVIDER:-keycloak}"))
        assertTrue(testRunnerRuntime.contains("KEYCLOAK_INTERNAL_URL: \${KEYCLOAK_INTERNAL_URL:-http://keycloak:8080}"))
        assertTrue(authGatewayRuntime.contains("OAUTH2_PROXY_OIDC_ISSUER_URL: https://keycloak.\${DOMAIN}/realms/webservices"))
        assertTrue(realmTemplate.contains("http://test-runner/callback"))
        assertTrue(realmTemplate.contains("http://test-runner-managed/callback"))
        assertFalse(testRunnerRuntime.contains("AUTHELIA_API_URL"))
    }

    @Test
    fun `wait ready resolves bundled common helper and run time stays in runtime dir`() {
        val repoRoot = repoRoot()
        val waitReadyText = Files.readString(repoRoot.resolve("scripts/lib/wait-ready.sh"))
        val deployText = Files.readString(repoRoot.resolve("scripts/deploy.sh"))

        assertTrue(waitReadyText.contains("LIB_DIR"))
        assertTrue(waitReadyText.contains("source \"\$LIB_DIR/common.sh\""))
        assertTrue(waitReadyText.contains("created_service_blockers"))
        assertTrue(waitReadyText.contains("service_is_completion_dependency_job"))
        assertTrue(waitReadyText.contains("service_completed_successfully"))
        assertTrue(waitReadyText.contains("awaiting "))
        assertFalse(waitReadyText.contains("webservices-next"))
        assertTrue(deployText.contains("ensure_runtime_links"))
        assertTrue(deployText.contains("deploy_state_bootstrap_missing_global_signature"))
        assertTrue(deployText.contains("runtime/stack.env"))
    }

    @Test
    fun `MatrixRTC services are app services and no Jitsi services are present`() {
        val graphText = Files.readString(repoRoot().resolve("stack.systemd/graph.json"))
        val appsTarget = Regex(
            """"name":\s*"webservices-apps\.target".*?"services":\s*\[(.*?)\]""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        ).find(graphText)?.groupValues?.get(1)
            ?: error("missing webservices-apps.target services")

        assertTrue(appsTarget.contains("\"livekit\""))
        assertTrue(appsTarget.contains("\"matrix-rtc-auth\""))
        assertFalse(graphText.contains("\"jitsi\""))
        assertFalse(graphText.contains("\"jicofo\""))
        assertFalse(graphText.contains("\"jvb\""))
        assertFalse(graphText.contains("\"prosody\""))
        assertFalse(Regex(""""excludedServices":\s*\[[^\]]*"livekit"""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(graphText))
        assertFalse(Regex(""""excludedServices":\s*\[[^\]]*"matrix-rtc-auth"""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(graphText))
        assertFalse(Regex(""""onDemandServices":\s*\[[^\]]*"livekit"""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(graphText))
        assertFalse(Regex(""""onDemandServices":\s*\[[^\]]*"matrix-rtc-auth"""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(graphText))
    }

    @Test
    fun `runtime startup gates reserve health checks for stateful prerequisites and init jobs`() {
        val repoRoot = repoRoot()
        val embedding = Files.readString(repoRoot.resolve("stack.runtime.yaml"))
        val pipeline = Files.readString(repoRoot.resolve("stack.runtime.yaml"))
        val opensearch = Files.readString(repoRoot.resolve("stack.runtime.yaml"))
        val jupyterhub = Files.readString(repoRoot.resolve("stack.runtime.yaml"))
        val bookstack = Files.readString(repoRoot.resolve("stack.runtime.yaml"))
        val caddyfile = Files.readString(repoRoot.resolve("stack.config/caddy/Caddyfile"))
        val graph = Files.readString(repoRoot.resolve("stack.systemd/graph.json"))

        assertTrue(embedding.contains("image: webservices/embedding-bge:local-build"))
        assertTrue(embedding.contains("BAAI/bge-m3"))
        assertTrue(embedding.contains("--dtype float32"))
        assertTrue(embedding.contains("start_period: 300s"))
        assertTrue(opensearch.contains("image: opensearchproject/opensearch"))
        assertTrue(opensearch.contains("OPENSEARCH_INITIAL_ADMIN_PASSWORD: \${OPENSEARCH_ADMIN_PASSWORD}"))
        assertFalse(opensearch.contains("inference-gateway"))
        assertTrue(jupyterhub.contains("DOCKER_NETWORK_NAME: webservices_ai"))
        assertTrue(bookstack.contains("API_REQUESTS_PER_MIN: \${BOOKSTACK_API_REQUESTS_PER_MIN:-1200}"))
        assertTrue(caddyfile.contains("models.{\$DOMAIN}"))
        assertTrue(caddyfile.contains("reverse_proxy embedding-gpu:8080"))
        assertFalse(caddyfile.contains("open-webui.{\$DOMAIN}"))
        assertFalse(caddyfile.contains("reverse_proxy inference-gateway:8111"))
        assertTrue(pipeline.contains("airflow-webserver:"))
        assertTrue(pipeline.contains("airflow-scheduler:"))
        assertTrue(pipeline.contains("ingestion-runner:"))
        assertFalse(pipeline.contains("embedding-worker:"))
        assertFalse(pipeline.contains("inference-gateway"))
        assertTrue(graph.contains(""""onDemandServices": []"""))
    }

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
