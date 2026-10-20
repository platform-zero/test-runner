#!/usr/bin/env python3
"""SOURCE ONLY sanity assertions; actual Kotlin compile/JUnit are separate."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
suites=root/'stack.kotlin/test-runner/src/main/kotlin/org/webservices/testrunner/suites'
registry=(suites/'ManagedSuiteRegistry.kt').read_text()
assert 'setOf("vaultwarden")) {' in registry
assert 'invokeOptionalModuleSuite("org.webservices.testrunner.suites.SecurityTestsKt", "securityTests")' in registry
assert '{ securityTests() }' not in registry
assert 'throw failure.targetException' in registry and 'Class.forName(fileClass).getMethod' in registry
assert 'if (isSelectedComponent("vaultwarden")) {' in (suites/'AuthenticationTests.kt').read_text()
profile=(suites/'TestdevProfile.kt').read_text()
assert 'firstOrNull(environment::containsKey)' in profile and 'Invalid component selection authority' in profile
assert 'primitive.isString' in profile and 'components.isNotEmpty()' in profile
assert 'names.size == names.toSet().size' in profile and 'Files.isSymbolicLink' in profile
assert 'getOrNull()' not in profile
for name in ['ComponentSelectionTest.kt','OptionalSuiteLinkageTest.kt']:
 assert (root/'stack.kotlin/test-runner/src/test/kotlin/org/webservices/testrunner/suites'/name).is_file()
print('SOURCE ONLY: optional cut linkage/selection sanity passed; no native application or SSO acceptance')
