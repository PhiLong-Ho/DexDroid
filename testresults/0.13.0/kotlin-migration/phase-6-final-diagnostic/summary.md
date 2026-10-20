# Final diagnostic checkpoint

Current source-bound V2 emulator input is132curated files, debug APK
1e76ec...afada and test APKa05e18...02ae. Complete native split passes on
Android11/API30 (257.164s) and Android16/API36 (237.435s). Android17/API37
focused input passes (46.129s); its earlier experimental teardown failure is
retained, not resolved by that focused result.

Both split histories retain one fixture identity, three fresh key sequences,
six native callbacks and all original cancellation/late-UP/no-duplicate
assertions. Android11's parent diagnostic capture remains inconclusive because
later performance callbacks exhaust the bounded lifecycle logger. Its native
suite pass is separate. Android16's filtered capture completes but lacks shell
decision records; emulator producer identities were not sampled. No lossless
transport, ordinary performance or hardware root-cause claim follows.

Current qualityCheck passes: formatting, lint, debug/test compilation and148JVM
tests. Tooling has208passes and one Windows symlink skip (209reported cases).
An earlier stale-coverage error was corrected after formatting; the complete
tooling rerun passes. Unsigned production APK/AAB definition guards pass.
Main diagnostics are DEBUG gated, a separate claim from source-set exclusion.

The non-restoring private sign/prepare/suite helper has65passing synthetic
procedure cases, including canceled collectors with command/finalization errors.
Concrete architecture preflight approves this approach at2026-10-18T18:05:35Z.
These are not OS, signing or phone results. The original hardware campaign still
has10passes/1split failure and unrun split tails/performance. Fresh exact reviews
precede new key/device use. One focused split reproduction precedes the required
full11 and focused checks; Chrome/cable/production delivery remain planned tails.
User handles restoration. No further replay/downgrade/product uninstall/clear.

Current input and operation Mermaid renders were inspected. No user-facing UI
change or new screenshot-baseline approval occurs. See the
[migration record](../../../../docs/changes/kotlin-migration.md) and
[operation contract](../../../../docs/changes/kotlin-migration-final-operations.json).

The [archive descriptor](archive.json) binds363files, SHA-256
454f1871199ad6bbe7c6b742e21552ba48286e567a68d1f4ac659271e65e04cc.
Raw logs/APKs/captures and historical failed recovery execution are private local
only under ignored artifacts/evidence/kotlin-phase-6-final-diagnostic/evidence.zip,
reviewer-accessible until owner removal; no GitHub upload/retention is claimed.
Earlier checkpoints remain immutable. This checkpoint does not admit release.
