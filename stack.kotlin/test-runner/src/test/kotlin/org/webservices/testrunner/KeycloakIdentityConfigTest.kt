package org.webservices.testrunner

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeycloakIdentityConfigTest {
    private val retiredDirectoryId = "ld" + "ap"
    private val retiredDirectoryEnvPrefix = retiredDirectoryId.uppercase() + "_"
    private val retiredAccountManagerId = retiredDirectoryId + "-account-manager"

    @Test
    fun `keycloak service is a default core identity service`() {
        val runtime = moduleFileText("keycloak", "stack.runtime.yaml")
        val caddyRuntime = moduleFileText("caddy", "stack.runtime.yaml")
        val caddyfile = repoFileText("stack.config/caddy/Caddyfile")
        val hosts = repoFileText("stack.containers/test-runner/fixtures/caddy-hosts.txt")
        val authGateway = moduleFileText("keycloak-auth-gateway", "stack.runtime.yaml")
        val configureRuntime = repoFileText("stack.config/keycloak/configure-runtime.sh")

        assertTrue(runtime.contains("keycloak-bootstrap:"))
        assertTrue(runtime.contains("image: webservices/keycloak:local-build"))
        assertTrue(runtime.contains("containerfile: ./stack.containers/keycloak/Containerfile"))
        assertTrue(runtime.contains("restart: \"no\""))
        assertTrue(runtime.contains("keycloak-bootstrap: \"completed\""))
        assertTrue(runtime.contains("KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak"))
        assertTrue(runtime.contains("./configs/keycloak/realm:/opt/keycloak/data/import:ro"))
        assertTrue(runtime.contains("- --import-realm"))
        assertTrue(runtime.contains("KC_HOSTNAME: https://keycloak.\${DOMAIN}"))

        assertTrue(caddyRuntime.contains("- keycloak.\${DOMAIN}"))
        assertTrue(caddyRuntime.contains("- keycloak-auth.\${DOMAIN}"))
        assertTrue(caddyRuntime.contains("- keycloak-whoami.\${DOMAIN}"))
        assertTrue(caddyfile.contains("keycloak.{\$DOMAIN}"))
        assertTrue(caddyfile.contains("reverse_proxy keycloak:8080"))
        assertTrue(caddyfile.contains("(keycloak_auth)"))
        assertTrue(caddyfile.contains("(keycloak_auth_base)"))
        assertTrue(caddyfile.contains("(keycloak_group_allow)"))
        assertTrue(caddyfile.contains("forward_auth keycloak-auth-gateway:4180"))
        assertTrue(caddyfile.contains("header X-Trusted-Proxy-Secret {\$MODEL_CONTEXT_PROXY_AUTH_SECRET:__disabled__}"))
        assertTrue(caddyfile.contains("remote_ip {\$TRUSTED_PROXY_SOURCE_RANGES:127.0.0.1/32 ::1/128}"))
        assertTrue(caddyfile.contains("request_header -X-Trusted-Proxy-Secret"))
        assertTrue(caddyfile.contains("import keycloak_auth grafana"))
        assertFalse(caddyfile.contains("import authelia_auth"))
        assertFalse(caddyfile.contains("forward_auth authelia:9091"))
        assertTrue(caddyfile.contains("keycloak-whoami.{\$DOMAIN}"))
        assertTrue(hosts.lineSequence().any { it.trim() == "keycloak" })
        assertTrue(hosts.lineSequence().any { it.trim() == "keycloak-auth" })
        assertTrue(hosts.lineSequence().any { it.trim() == "keycloak-whoami" })
        assertTrue(authGateway.contains("quay.io/oauth2-proxy/oauth2-proxy:v7.15.2@sha256:"))
        assertTrue(authGateway.contains("OAUTH2_PROXY_PROVIDER: keycloak-oidc"))
        assertTrue(authGateway.contains("OAUTH2_PROXY_SCOPE: \"openid profile email groups\""))
        assertTrue(authGateway.contains("--provider-ca-file=/ca/caddy-ca.crt"))
        assertTrue(authGateway.contains("--use-system-trust-store=true"))
        assertTrue(authGateway.contains("OAUTH2_PROXY_CLIENT_SECRET: \${OAUTH2_PROXY_CLIENT_SECRET:-}"))
        assertTrue(authGateway.contains("OAUTH2_PROXY_COOKIE_SECRET: \${OAUTH2_PROXY_COOKIE_SECRET:-}"))
        assertTrue(authGateway.contains("OAUTH2_PROXY_COOKIE_NAME: _webservices_edge"))
        assertTrue(authGateway.contains("OAUTH2_PROXY_COOKIE_DOMAINS: \${DOMAIN},.\${DOMAIN}"))
        assertTrue(authGateway.contains("OAUTH2_PROXY_WHITELIST_DOMAINS: \${DOMAIN},.\${DOMAIN}"))
        assertTrue(authGateway.contains("OAUTH2_PROXY_SESSION_STORE_TYPE: redis"))
        assertTrue(authGateway.contains("OAUTH2_PROXY_REDIS_CONNECTION_URL: redis://default:\${VALKEY_ADMIN_PASSWORD}@valkey:6379/2"))
        assertTrue(authGateway.contains("OAUTH2_PROXY_PASS_ACCESS_TOKEN: \"false\""))
        assertTrue(authGateway.contains("OAUTH2_PROXY_PASS_AUTHORIZATION_HEADER: \"false\""))
        assertTrue(authGateway.contains("- valkey"))
        assertTrue(authGateway.contains("valkey:"))
        assertTrue(configureRuntime.contains("ensure_confidential_client"))
        assertTrue(configureRuntime.contains("\"webservices-onboarding-marker\""))
        assertTrue(configureRuntime.contains("\"eventsEnabled\": true"))
        assertTrue(configureRuntime.contains("\"webservices-edge\""))
        assertTrue(configureRuntime.contains("https://keycloak-auth.\$DOMAIN/oauth2/callback"))
        assertTrue(configureRuntime.contains("ensure_group \"developers\""))
        assertFalse(configureRuntime.contains("jq "))

        assertFalse(runtime.contains("authelia"))
        assertFalse(runtime.contains(retiredDirectoryId))
    }

    @Test
    fun `keycloak realm import keeps groups and smoke client reproducible`() {
        val realm = repoFileText("stack.config/keycloak/realm/webservices-realm.json.template")
        val smoke = repoFileText("stack.containers/test-runner/playwright-tests/tests/deep/oidc/keycloak.spec.ts")
        val exportHelper = repoFileText("stack.config/keycloak/export-realm.sh")

        assertTrue(realm.contains("\"realm\": \"webservices\""))
        assertTrue(realm.contains("\"alias\": \"UPDATE_PASSWORD\""))
        assertTrue(realm.contains("\"alias\": \"CONFIGURE_TOTP\""))
        assertTrue(realm.contains("\"name\": \"admins\""))
        assertTrue(realm.contains("\"name\": \"operators\""))
        assertTrue(realm.contains("\"name\": \"users\""))
        assertTrue(realm.contains("\"name\": \"developers\""))
        assertTrue(realm.contains("\"name\": \"agents\""))
        assertTrue(realm.contains("\"name\": \"onboarding_required\""))
        assertTrue(realm.contains("\"clientId\": \"test-runner\""))
        assertTrue(realm.contains("\"claim.name\": \"groups\""))

        assertTrue(smoke.contains("KEYCLOAK_REALM"))
        assertTrue(smoke.contains("KEYCLOAK_URL"))
        assertTrue(smoke.contains("/realms/\${keycloakRealm}/.well-known/openid-configuration"))
        assertTrue(exportHelper.contains("Do not commit users, credentials, sessions, private keys, or client secrets."))
    }

    @Test
    fun `onboarding is authenticated and event marker driven`() {
        val onboardingRuntime = moduleFileText("onboarding", "stack.runtime.yaml")
        val caddyfile = repoFileText("stack.config/caddy/Caddyfile")
        val containerfile = repoFileText("stack.containers/keycloak/Containerfile")
        val listenerFactory = repoFileText("stack.kotlin/keycloak-onboarding-listener/src/main/java/org/webservices/keycloak/onboarding/OnboardingMarkerEventListenerProviderFactory.java")
        val listener = repoFileText("stack.kotlin/keycloak-onboarding-listener/src/main/java/org/webservices/keycloak/onboarding/OnboardingMarkerEventListenerProvider.java")
        val onboardingService = repoFileText("stack.containers/onboarding-service/onboarding_service.py")

        assertFalse(onboardingRuntime.contains("ONBOARDING_SELF_SERVICE_ENABLED"))
        assertFalse(onboardingRuntime.contains("ONBOARDING_INVITES_JSON"))
        assertFalse(onboardingRuntime.contains("KEYCLOAK_ADMIN_PASSWORD"))
        assertTrue(onboardingRuntime.contains("onboarding_data:/data"))

        assertFalse(caddyfile.contains("@onboarding_public path /start /api/invites/signup"))
        assertTrue(caddyfile.contains("import keycloak_auth_base onboarding"))
        assertTrue(caddyfile.contains("redir @onboarding_required_{args[0]} https://onboarding.{\$DOMAIN}/ temporary"))
        assertTrue(caddyfile.contains("redir @onboarding_required_legacy_{args[0]} https://onboarding.{\$DOMAIN}/ temporary"))

        assertTrue(containerfile.contains("/opt/keycloak/providers/keycloak-onboarding-listener.jar"))
        assertTrue(containerfile.contains("kc.sh build"))
        assertTrue(listenerFactory.contains("webservices-onboarding-marker"))
        assertTrue(listener.contains("getRequiredActionsStream()"))
        assertTrue(listener.contains("user.joinGroup(marker)"))
        assertTrue(listener.contains("user.leaveGroup(marker)"))

        assertTrue(onboardingService.contains("Finish account setup in Keycloak"))
        assertTrue(onboardingService.contains("account creation is admin managed"))
        assertFalse(onboardingService.contains("ONBOARDING_INVITES_JSON"))
        assertFalse(onboardingService.contains("create_keycloak_user"))
    }

    @Test
    fun `postgres keycloak database bootstrap supports fresh and existing deployments`() {
        val postgresCompose = moduleFileText("postgres", "stack.runtime.yaml")
        val initDb = repoFileText("stack.config/postgres/init-db.sh")
        val ensureDb = repoFileText("stack.config/postgres/ensure-keycloak-db.sh")

        assertTrue(postgresCompose.contains("POSTGRES_KEYCLOAK_PASSWORD: \${POSTGRES_KEYCLOAK_PASSWORD:-}"))
        assertTrue(initDb.contains("POSTGRES_KEYCLOAK_PASSWORD=\"\${POSTGRES_KEYCLOAK_PASSWORD:-}\""))
        assertTrue(initDb.contains("CREATE USER keycloak"))
        assertTrue(initDb.contains("CREATE DATABASE keycloak OWNER keycloak"))
        assertTrue(initDb.contains("GRANT ALL ON SCHEMA public TO keycloak"))

        assertTrue(ensureDb.contains("POSTGRES_KEYCLOAK_PASSWORD=\"\${POSTGRES_KEYCLOAK_PASSWORD:?ERROR: POSTGRES_KEYCLOAK_PASSWORD not set}\""))
        assertTrue(ensureDb.contains("CREATE USER keycloak"))
        assertTrue(ensureDb.contains("CREATE DATABASE keycloak OWNER keycloak"))
        assertTrue(ensureDb.contains("GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak"))
    }

    @Test
    fun `retired directory user migration tooling is not bundled after one time cutover`() {
        assertFalse(
            Files.exists(repoRoot().resolve("stack.config/keycloak/migrate-${retiredDirectoryId}-users.sh")),
            "Directory-to-Keycloak user migration was a one-time operator action; do not bundle reusable migration tooling"
        )
    }

    @Test
    fun `retired directory runtime services are removed while Keycloak backed groupware is restored`() {
        val root = repoRoot()
        val caddyRuntime = moduleFileText("caddy", "stack.runtime.yaml")
        val caddyfile = repoFileText("stack.config/caddy/Caddyfile")
        val sogoCompose = moduleFileText("sogo", "stack.runtime.yaml")
        val mailserverCompose = moduleFileText("mailserver", "stack.runtime.yaml")
        val homeassistantCompose = moduleFileText("homeassistant", "stack.runtime.yaml")
        val testRunnerRuntime = moduleFileText("test-runners", "stack.runtime.yaml")
        val networkSettings = repoFileText("global.settings/networks.yml")

        assertFalse(Files.exists(root.resolve("runtime.overlays/$retiredDirectoryId.yml")))
        assertFalse(Files.exists(root.resolve("stack.config/homeassistant/auth_${retiredDirectoryId}.py")))
        assertFalse(Files.exists(root.resolve("stack.kotlin/test-runner/src/main/kotlin/org/webservices/testrunner/framework/${retiredDirectoryId.replaceFirstChar { it.uppercase() }}Helper.kt")))

        assertTrue(caddyRuntime.contains("sogo.\${DOMAIN}"))
        assertFalse(caddyRuntime.contains("$retiredDirectoryId:"))
        assertTrue(caddyfile.contains("sogo.{\$DOMAIN}"))
        assertTrue(sogoCompose.contains("SOGO_OAUTH_SECRET"))
        assertTrue(sogoCompose.contains("keycloak-configure"))
        assertFalse(mailserverCompose.contains("ACCOUNT_PROVISIONER: ${retiredDirectoryId.uppercase()}"))
        assertFalse(mailserverCompose.contains(retiredDirectoryEnvPrefix))
        assertFalse(homeassistantCompose.contains(retiredDirectoryEnvPrefix))
        assertFalse(testRunnerRuntime.contains(retiredDirectoryEnvPrefix))
        assertFalse(networkSettings.contains("  $retiredDirectoryId:"))
    }

    @Test
    fun `service routes enforce keycloak group rbac at the edge`() {
        val caddyfile = repoFileText("stack.config/caddy/Caddyfile")
        val donetickCompose = moduleFileText("donetick", "stack.runtime.yaml")
        val erpnextBootstrap = repoFileText("stack.config/erpnext/bootstrap-site.sh")

        assertTrue(caddyfile.contains("Jellyfin password login is disabled; use Keycloak SSO"))
        assertTrue(caddyfile.contains("request_header Remote-User {http.request.header.X-Remote-User}"))
        assertTrue(caddyfile.contains("header_up X-Remote-User {header.Remote-User}"))
        assertTrue(caddyfile.contains("not header_regexp Remote-Groups (^|.*[,[:space:]])({args[1]})([,[:space:]].*|$)"))
        assertTrue(caddyfile.contains("import keycloak_group_allow donetick users|operators|admins"))
        assertTrue(caddyfile.contains("import keycloak_group_allow erpnext admins|operators"))
        assertTrue(caddyfile.contains("import keycloak_group_allow jupyterhub admins|operators|developers"))
        assertTrue(caddyfile.contains("import keycloak_group_allow search admins|operators|agents"))
        assertTrue(caddyfile.contains("import keycloak_group_allow pipeline admins|operators"))
        assertTrue(caddyfile.contains("vars donetick_upstream_authorization {http.request.header.Authorization}"))
        assertTrue(caddyfile.contains("header_up Authorization {vars.donetick_upstream_authorization}"))
        assertTrue(donetickCompose.contains("DT_OAUTH2_CLIENT_ID: donetick"))
        assertTrue(donetickCompose.contains("DT_OAUTH2_SCOPES: openid profile email groups"))
        assertTrue(donetickCompose.contains("DONETICK_DISABLE_SIGNUP: \"true\""))
        assertTrue(erpnextBootstrap.contains("promoting Keycloak-created users to ERPNext desk users"))
        assertTrue(erpnextBootstrap.contains("user.user_type = \"System User\""))
        listOf("Desk User", "Employee", "Projects User").forEach { requiredRole ->
            assertTrue(
                erpnextBootstrap.contains("\"$requiredRole\""),
                "ERPNext bootstrap must grant the $requiredRole role to eligible Keycloak-created desk users"
            )
        }
    }

    private fun repoFileText(relativePath: String): String = TestSourceFiles.repositoryText(relativePath)

    private fun moduleFileText(moduleId: String, relativePath: String): String =
        TestSourceFiles.moduleText(moduleId, relativePath)

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
