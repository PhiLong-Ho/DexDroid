# Kotlin migration phase 2

Eleven models/preferences/parsers and ten JVM test files now use Kotlin. All 148
JVM cases passed; 94 original non-private descriptors/static calling shapes were
preserved across twenty named owners. Structured tooling: 169 passes and one
Windows platform skip. No lint baseline changed. Two native Java harness repairs
preserve initialized Shizuku readiness and coherent main-thread profile assertions;
their explicit deltas must survive phase 5.

Final product SHA256: `d8ce99c6d409ec16ce034660c953c9d69075d9aec891cf783e9450540b8dfd37`.
Final test SHA256: `f73ad6bebc6f63a0cfee7b8b9bf72fcfeba2d78b6bdecfb71cdf0e3d2c3a7569`.
Earlier generations and their results are separate, not reused as byte identity.

| Ordinary configuration | Final-pair suites | Outcome |
| --- | --- | --- |
| API 30 AOSP, software renderer | input `095048`, split `095121`, profiles `095550` | All three passed |
| API 36, host renderer | profiles `091814`, split `091851`, input `092247` | All three passed |
| API 37, software renderer | input `092348`, profiles `092442` | Both passed; experimental platform |

Run identifiers use UTC date `20261018`; full names, exact configurations, APKs,
source snapshots and outcomes are in the private bundle. The AOSP image is
`system-images/android-30/default/x86_64`, installed revision 11 (embedded source
metadata says 10), ordinary vsync, LatinIME, emulator 37.2.12 and secondary
1920×1200/160. Shizuku remains shell UID 2000. This is eight selected suites,
not full regression or general Android 17 compatibility.

Retain failures: the Google API 30 configuration produced profile MOVE ANRs and
a split reader-handoff return with a genuinely missing controller surface. The
same final harness reproduced both against the earlier Java-model product. The
AOSP Java-model control also failed profiles, then passed full split; the current
pair separately passed all three selected AOSP suites. Causes remain unknown.
Early ANRs, compile repairs, service-readiness/observer failures, old-pair passes,
intrusive diagnostics and the setup-only false-vsync probe are retained. None is
relabeled a clean pass. Failed suites leave their later scenario tails unrun.

Independent architecture reviewers approved only bounded continuation and the
conditional ordinary AOSP comparison. [The migration plan](../../../../docs/changes/kotlin-migration.md)
records exact decisions, signature limits, affected-owner retests and final
hardware/release obligations. The passing AOSP split gate needs no broad split
exception. Existing failures cannot be claimed fixed by conversion.

Root inspected selected current phone/monitor profiles, keyboard, layout, four
rotations and pane/idle states. Appearance is consistent in that bounded review;
the full visual catalog and physical acceptance remain pending. Injected physical-
host DOWN to independent fixture dispatch: 100 samples after 10 warmups,
p50 61 ms / p95 84 ms on API 30 AOSP. This excludes rendering, broadcast/await,
physical fingers, touch-to-photon and frame pacing.

The verified [archive descriptor](archive.json) binds private local source/APK/run
evidence, retained failures/controls/diagnostics, ABI/JVM/tooling and rendered
diagrams. No device capture was published. Physical Android 11 testing remains
reserved for the final migration campaign.
