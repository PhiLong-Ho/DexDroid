# Xiaomi state-specific verification generation

Initial installation passes with product/test/manager UIDs10361/10362/10363.
The v1 split wrapper fails during read-only package admission: a substring query
returns both root and test package rows. No native instrumentation ran. Preserve
its terminal state and all12 read-only command journals.

The v2 helper parses strict rows and selects the exact package. Its prepare phase
only verifies/adopts the bound successful installation, historical UIDs, exact APK
bytes and shell broker privileges. It does not install, sign, restart the broker
or replay preferences. Then the original ordered native phases may start after
new operations reviews. Current89 synthetic actual-function cases pass, including
observed row orders/returned UIDs, absence, malformed/duplicate rows, changed
adoption/UID/installed/privilege state, native/cleanup/collection/clock failures and
terminal rejection. Earlier calibration expectation failure remains retained.

[Private archive](archive.json):727 files, SHA256
`292b3a020c8ef51e5cb4c57526b66824c9f86edec85216c6ba7ed36dfb05a3fe`.
Raw data stays in `artifacts/evidence/kotlin-migration-xiaomi-preflight-v3/evidence.zip`,
available locally until owner removal with no automatic expiry/upload. Source,
APK, frozen inputs, original installation/failure journals, current calibration
and inspected diagram renders are included. The initial preflight archive remains
separate. Native tests and final release approval remain pending; Android15
hardware is not Android11 acceptance. See the [change record](../../../../docs/changes/xiaomi-migration-acceptance.md)
for architecture review and scope.
