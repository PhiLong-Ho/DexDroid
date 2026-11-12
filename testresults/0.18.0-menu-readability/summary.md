# Menu readability — physical Xiaomi Android 16

Development verification on 2026-11-12; not a release or full regression pass.
Baseline commit `c05649ce804f077700db50676827b48a812ac321`, dirty feature branch
`feature/menu-readability`; app version remains 0.18.0/build 51 Debug.
The device reports Xiaomi 14 model `23127PN0CG`, Android 16/API 36, HyperOS
`OS3.0.305.0.WNCMIXM`. Testing used its physical external 1920×1200 touchscreen,
logical UI DPI 240 and existing authorized Shizuku connection. No personal app
was opened for this readability acceptance; routine fixtures stayed private.

## Outcomes

| Suite | Run ID (UTC) | Result | Instrumentation duration |
| --- | --- | --- | --- |
| Phone UI | `20261112T142402.587485Z` | Pass | 35.203 s |
| Monitor | `20261112T142441.188992Z` | Pass | 111.891 s |
| Diagnostics | `20261112T142203.135646Z` | Pass | 22.187 s |
| Capabilities | `20261112T142226.562715Z` | Pass | 15.703 s |
| Audio | `20261112T142635.387089Z` | Pass | 23.234 s |
| Non-destructive debugging | `20261112T142659.863747Z` | Pass | 27.375 s |
| Workspace | `20261112T142243.500660Z` | Fail: restored fixture native input admission | 41.047 s |

These are suite outcomes, not counts of independent assertions. Monitor coverage
includes native contacts/navigation, all four output rotations and a separate
1920×1080/UI-DPI-240 Fit preview. Phone/monitor checks cover labeled Settings
vectors, all 13 guide topics, four passive diagrams, current values, scrolling,
related-help parent restoration, header targets and Close/outside behavior.
Native constructed-guide measurement passes six configurations: 240/360 dp widths
with 1.0/1.3/2.0 fonts. It does not certify whole-app accessibility/font scaling.
Debugging acceptance uses controlled helper outcomes and does not switch off ADB.

Workspace reproduction: save a single fixture at rotation 0, stop the desktop,
restore through the saved shortcut, observe restored task/display/axes, then wait
for `settledInputWindow` before the first contact. The last wait times out. Cause
is unresolved; this is not recast as a pass or attributed to a specific product
change. Its initial empty-state phone layout was inspected separately.

`spotlessApply qualityCheck` passed after the final test edits (9 s; 96 tasks,
14 executed/82 up-to-date). JVM: 287 passed, no failures/errors/skips. Tooling:
203 passed, 8 skipped on Windows, 211 total (20.368 s). Requirement mapping,
coverage freshness, documentation and privacy guards passed separately. Existing
Kotlin future-version warnings and lint-baseline hints remain; no baseline edit.

## Exact build and reuse scope

Final installed app SHA-256:
`67f27a3634d5e09d271fb7e2107c1564aee400150b5f0d0d465644bb93a4f262`.
Final test APK SHA-256:
`e5b58f0638537e11126221964b3f0c6c1209e0a6589f5af71b34bd35029dec9b`.
Both were signed with the existing app certificate for in-place installation;
signature-only payload equality was checked. Data was not uninstalled.

Diagnostics/capabilities and the failed workspace attempt used an earlier signing
envelope. Its app's non-signature ZIP member payloads were compared and are equal
to the final app. Their suite sources were unchanged; the later test-only edit
waits for submitted guide frames before captures. This is explicit reuse of
those original scoped runs, not new execution. The archive member
`20261112-review/source-build-inputs.json` records every app/build source hash,
APK hashes and that comparison. Each device run records installed APK identity,
configuration and actual command/result; the dirty baseline commit alone is not
claimed as a source identity.

## Rendered review and limitations

The author inspected actual phone Settings, guide, setup and shared-panel renders;
all four diagram types across four monitor rotations were inspected in a labeled
orientation-normalized contact sheet and selected original PNGs. Current references
and hashes are in [the UI reference index](../../docs/ui-references/menu-readability.json).
Earlier guide captures were one rendered frame behind their names; the test now
waits for animation callbacks and asserts full diagram visibility. Superseded
captures and all failed installation/assertion attempts remain in the archive.
Five source-linked architecture diagrams were rendered and inspected separately.

No human-approved pixel golden, TalkBack/device accessibility, complete DPI
160/240/320 matrix, other OEM/OS certification or measured settled-menu latency
is claimed. No emulator result substitutes for this physical-device test.
No production version/release, commit, push or final publication approval is claimed.
The separate test package was removed and the phone controller reopened; the
development app remains installed for the user to review.

## Retrieval and commands

[Archive descriptor](archive.json): 508 files, ZIP SHA-256
`5241b7889f986bdb36de5b7790981ef142694795a8d22fd077a682d0c802c58a`.
The descriptor points to a private ignored local ZIP, retained until separately
removed. No upload, public screenshot or remote retention guarantee exists.
Verification checks archive/member hashes, not correctness or human approval.

```powershell
./gradlew.bat spotlessApply qualityCheck
python tools/run_tool_tests.py --output testresults/0.18.0-menu-readability/runs/20261112-host/host/tool-tests.json
python tools/run_device_checks.py --adb <sdk>/platform-tools/adb.exe --serial <xiaomi> --suite <suite> --version 0.18.0-menu-readability --timeout 480
python tools/coverage.py update
python tools/coverage.py check
python tools/check_requirements.py
python tools/check_documentation.py
python tools/check_privacy.py
python tools/evidence.py verify --archive artifacts/evidence/menu-readability-20261112-final/evidence.zip --descriptor testresults/0.18.0-menu-readability/archive.json
```

Actual expanded device commands, explicit private Python/SDK paths, installation
attempts and time limits remain in each raw run. Initial installs used `--install`;
later suites verified matching installed hashes before running sequentially.
See [the change record](../../docs/changes/menu-readability.md) for preflight,
implementation, failures and architecture scope.
