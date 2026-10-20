# Kotlin migration: phase 4b checkpoint

MainActivity, MonitorActivity and MonitorShell are converted: **47/82 maintained
Android sources**, including 36/38 main sources, now use Kotlin. SessionService,
SplitWorkspace and native instrumentation remain Java for their separate gates.
No physical phone was used.

Frozen product: `f0fb0a4292bf3e99cba44c377b62634dbb6d835c33e6728e0be868b7e2c7e304`.
Test APK: `f85829e8fee729c01e92b2b00f4fec05d9abe11e2256cede19cc98f2805e7090`.
The private `phase-4b-inputs` manifest binds APKs and 121 source/tooling paths,
rechecked unchanged after native testing. Instrumentation bytes match phase4a.

| Configuration | Selected passing outcomes |
| --- | --- |
| API30 AOSP/software | input, ui, monitor, keyboard, catalog, profiles, full split, idle |
| API36 host | input, ui, monitor, keyboard, catalog, profiles, full split, idle |
| API37 software, experimental | input, ui, monitor, keyboard, idle |

These are **21 selected current outcomes**, without reuse. Exact run identities,
commands, timings and label counts are in private `phase-4b-results/index.json`.
This is not the final complete acceptance inventory. API37 split, profiles and
other suites are unrun here; its prior compatibility gaps are not waived.
There were no failed native attempts in this checkpoint.

Host formatting, compilation, lint and **148 JVM cases** pass. ABI comparison
passes **six named owners/56 original non-private descriptors/static shapes**.
Separate javap inspection retains reflected dialog/listener fields, private menu
overloads, and the MainActivity.Pad/MonitorShell.SearchEditor outer constructors.
Compiler deprecation/nullable-generic warnings remain; lint has no active errors
or warnings. Structured tooling records **169 passes/one platform skip**;
requirements/coverage/privacy/Ruff checks pass. Raw outcomes stay in the archive.

Initial compiler and lint failures remain retained. Fresh independent architecture
review permits exactly seven inherited lint relocations plus three observed
occurrences; all 53 other raw baseline blocks remain byte-identical. No baseline
regeneration, suppression or severity change. This preserves inherited battery,
accessibility and localization debt rather than resolving it.

Nine actual phone/monitor renders were inspected across API30/36, including four
monitor rotations, native phone IME, touchpad actions and two-pane output. Bounded
comparison to preceding Settings/app-settings renders retains layout/style.
No whole-catalog approval, pixel-baseline promotion, animation or hardware claim.
API30 full split passes 3,047 labels/247.970s. Its 100 injected DOWN samples after
10 warmups measure **53ms p50/64ms p95**, excluding rendering, broadcast/await
and physical-finger latency; no performance improvement is inferred.

See the [migration plan](../../../../docs/changes/kotlin-migration.md) for actual
review identities, preceding unresolved failures and remaining obligations.
Raw failures, source/APK bindings, outcomes and captures stay in the verified
private [archive descriptor](archive.json), locally available until owner removal.
No public upload or Actions retention is claimed. Final complete emulator/hardware,
upgrade, production exclusion and fresh release/final reviews remain required.
