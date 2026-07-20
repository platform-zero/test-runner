# Test runner

Source module for Platform Zero's Kotlin and Playwright stack verification runner. The deployable container and runtime definition live in `test-runners-stack-module`; this repository supplies the mirrored Kotlin test-runner overlay.

## Validation

Run the complete module contract and security suite from a workspace containing the generator:

```sh
../sso-stack-generator/scripts/test-module.sh --all .
```

Deployed verification is exercised through the test-runners module rather than a standalone smoke test.
