# Private signing and missing-monitor precondition

Fresh final reviewers /root/kotlin_monitor_final_a at2026-10-18T18:28:39Z
and /root/kotlin_monitor_final_b at18:28:57Z approve candidate
3fbb4a4c91e5236fafa0ca08648c8f41b2e91f835fab8c2c9c679f44b0312194
for private sign/prepare/suite only. The actual approval record and approved
candidate snapshot are retained privately. No publication approval is implied.

Actual private signing passes for all three payloads with the existing certificate:

| Artifact | Signed SHA-256 |
| --- | --- |
| Debug | 184d1857b92474d2723723fee00a68464afbad6f467d36cd43cf76f6c3a112e1 |
| Device tests | d269b2c4e359e136d6f38ba4dde6a1cb72bba509d042daebbbf9a418bb514670 |
| Production input | 39e1dbc6b38da1e21af516908a51aca7b1c907bed3a0431b3f872cccb3376242 |

Refreshing local ADB discovery reveals a new working TLS port. The requested
Android11SHARK is reachable and unlocked. Actual preparation18:29:44Z fails at
the monitor guard: its raw display dump contains only the built-in physical
screen. The guard runs before helper staging, Stop, install or launch. None of
those mutations occurs and no native suite runs. This is a failed operation /
missing external precondition, not a new product failure or hardware pass.
The user has been asked to connect the monitor and leave the phone unlocked.

Keep this failed journal and signed inputs immutable. Do not reset terminal
flags or automatically retry. A separately reviewed fresh journal/consumer must
bind the immutable signed producer, current source/certificate/payload/monitor
and exact reviewed consumer candidate before further tests. User handles
restoration; no replay/downgrade/product uninstall/clear is planned.

The verified [archive descriptor](archive.json) binds18files, SHA-256
3ba5926c7e0c093aa9b91e79fba589f1a5f1a041cd25678dd422b19e2e4ce5c0.
It retains signed APKs, actual journals/raw observations, helpers/contract and
approval snapshot at ignored artifacts/evidence/kotlin-phase-6-private-signing-monitor-block/evidence.zip.
Storage is private local only, reviewer-accessible until owner removal. Nothing
is uploaded publicly. Earlier archives remain immutable; hardware tails,
Chrome/cable/delivery and publication remain unverified.
