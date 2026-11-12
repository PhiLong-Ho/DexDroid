# Settings search and Developer Mode label

2026-11-12 development verification on physical Xiaomi 14 `23127PN0CG`, Android
16/API 36, HyperOS `OS3.0.305.0.WNCMIXM`, actual 1920×1200 monitor and logical
UI DPI 240. Installed 0.18.0/build 51 Debug; no new release/full OS regression.
Dirty `feature/menu-readability`, baseline `c05649ce804f077700db50676827b48a812ac321`;
the archive's source hashes identify tested inputs, not that commit alone.

| Suite | Run ID (UTC) | Outcome | Native duration |
| --- | --- | --- | --- |
| Phone UI | `20261112T150052.324068Z` | Pass | 36.828 s |
| Monitor | `20261112T150437.013204Z` | Pass | 126.437 s |
| Existing Apps keyboard | `20261112T145826.326079Z` | Pass, scoped reuse below | 56.078 s |
| Non-destructive debugging | `20261112T145925.342257Z` | Pass, scoped reuse below | 27.907 s |

Phone/monitor checks cover related keywords, no results/Clear, count wording,
existing destination/confirmation behavior, child-Up query retention, Close reset
and retired-editor isolation. Actual phone IME and monitor local key contacts
exercise keyboard-first Back and no automatic reopening on child return. Monitor
runs four rotations and its separate 1920×1080 Fit preview; search is not crossed
with every rotation/resolution/DPI combination. Native guide width/font measurement
is not whole-app font certification. No physical Bluetooth keyboard was tested.

Host: `spotlessApply qualityCheck` passed on final product sources in 11 s
(96 tasks, 23 executed/73 up-to-date). **290 JVM passed; 203 tooling passed and
8 skipped** on Windows (211 total, 20.030 s). Total host outcomes: 493 passed/8
skipped, not 501 passes. Existing future-Kotlin warnings/lint-baseline hints remain;
the baseline was untouched. After the capture-only test edit, Spotless/native test
APK compilation passed in 3 s; full qualityCheck was not freshly repeated for
that helper-only delta. Current mapping/coverage, documentation and privacy guards pass.

## Exact build and reuse

Installed app: `8ca3b3707bbc72b9799b23d5454218f9297f20ff4b40fc45b2ff13d99846bbf3`.
Final monitor test APK: `8a0e7234c55335a2d56b7ddeda5d1ffe70f4f47b5569b3e085f1cfa5289155c1`.
Phone UI used the same product APK and unchanged phone test source before the
monitor capture-helper update. Each raw run retains installed hashes and commands.

Apps keyboard and controlled shutdown precede the singular-count wording correction,
its assertion and a render-boundary screenshot wait. Only those three source files
differ, as recorded in `20261112-review/source-build-inputs.json` against
`before-count-polish.json`. Routing, editor ownership, keyboard and shutdown code
were unchanged. These are explicit earlier scoped passes, not fresh final-APK
execution claims. Phone/monitor were rerun after wording; monitor again after
capture settling. Earlier Clear-icon captures taken before layout settled remain
retained but are superseded. Development signatures used the existing certificate
and verified non-signature payload equality; no app-data uninstall. The separate
test helper was removed and the phone controller reopened.

## Rendered scope and gaps

Author inspected phone result/no-result/native-keyboard views and eight monitor
keyboard/no-result configurations in a labeled orientation-normalized contact
sheet plus selected originals. [Current capture index](../../docs/ui-references/settings-search.json)
contains 11 hashes. Two updated controller diagrams were rendered and inspected.
That count belongs to this historical run; the live reference index may advance
with later releases. Original captures/hashes remain in this run's sealed archive.
No human pixel golden, measured latency, TalkBack, other OEM/OS or broad font/DPI
approval. Nonzero filtered scroll, Retry faults, Stop/disconnect during typing and
nonempty-query display rebuild are not exhaustively injected. The prior workspace
restore input-admission failure remains unresolved/unretested. No full-suite pass.

Review `R-1794494725-0-64974265253888` explicitly chose existing-page filtering,
fixed placement, Clear/no-results, retain on Up/reset on Close. Advisory preflight
does not substitute for final publication reviews. No commit/push/release requested.

## Private retrieval and commands

[Archive descriptor](archive.json): 408 files, SHA-256
`2b1dbf3cc1b9e531bfa1eb1afffe3c773897226eb066677eb207a55a02d43775`.
Verified ignored **local-only** ZIP, retained until separately removed; no uploads
or remote retention guarantee. Members include source/build manifests, preflight,
host outcomes, raw native runs and diagrams. Hash verification is not correctness.

```powershell
./gradlew.bat spotlessApply qualityCheck
./gradlew.bat spotlessApply :app:assembleDebugAndroidTest
python tools/run_tool_tests.py --output testresults/0.18.0-settings-search/runs/20261112-host/host/tool-tests.json
python tools/run_device_checks.py --adb <sdk>/platform-tools/adb.exe --serial <xiaomi> --suite <suite> --version 0.18.0-settings-search --timeout 480
python tools/coverage.py update
python tools/coverage.py check
python tools/check_requirements.py
python tools/check_documentation.py
python tools/check_privacy.py
python tools/evidence.py verify --archive artifacts/evidence/settings-search-20261112-final/evidence.zip --descriptor testresults/0.18.0-settings-search/archive.json
```

Expanded private paths, serial/install flags and limits remain in raw runs.
Devices ran sequentially and checked installed hashes. [Change record](../../docs/changes/settings-search.md)
contains actual expert identities, design decision and ownership scope.
