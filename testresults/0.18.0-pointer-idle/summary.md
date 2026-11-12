# Automatic pointer hiding

Development verification on 2026-11-13 Singapore time (raw native times are UTC
2026-11-12), physical Xiaomi 14 `23127PN0CG`, Android 16/API 36, HyperOS
`OS3.0.305.0.WNCMIXM`, actual 1920×1200 monitor and default single layout.
Installed 0.18.0/build 51 Debug in place, preserving app data. Dirty
`feature/menu-readability`, baseline `c05649ce804f077700db50676827b48a812ac321`.
Archive source hashes identify the tested inputs; that baseline alone does not.

## Actual outcomes

| Check | Outcome | Scope |
| --- | --- | --- |
| Final `spotlessApply qualityCheck` | Pass, 13 s | 96 tasks; 295 JVM passed, including five new policy tests; native acceptance compiled; lint baseline unchanged |
| Tooling | 203 passed, 8 Windows skips | 211 total, 19.141 s; 498 combined host passes, not 506 |
| Manual native phone Pad | Pass | Synthetic input fixture on the real monitor; idle hide, stationary hold, release idle, first wake tap |
| Instrumented input suite | Blocked, no tests ran | Two helper-install failures, `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user` |

The final app APK is
`eae1bcf340836a0bbf6e85648aacd01e514341cacb9af8dbe531fe14702406d4`;
the installed base APK hash was checked afterward. The compiled, uninstalled test
APK is `c01a8e640cf54d0975cc8245a9d8ed5d3f7be1b2a6e74da316a367f334b7f707`.
Development signatures used the existing certificate and verified identical
non-signature payloads. Blocked instrumentation attempts predate the final opaque
hold/guide app edit; they are retained failures, never final-APK suite passes.

Manual check `20261113-manual` opened the built-in synthetic fixture through
the phone Apps menu and drove the real phone Pad using Android `input -d 0`.
The physical monitor was captured by its observed SurfaceFlinger display ID,
not the focused-display default. Teal pixel counts changed from 18,464 constant
fixture pixels to 18,680 with the cursor, then back after six idle seconds.
A stationary raw DOWN remained visible beyond 5.7 seconds without MOVE events;
after UP and six idle seconds it hid. The first subsequent tap restored it.
Author-inspected hidden/held/wake screenshots show respectively no cursor and
zero taps, visible cursor and zero taps, then visible cursor and exactly one tap.
No personal app was opened. Initial default UI dump/capture may target the
external display; explicit physical captures and the later actual phone Apps
hierarchy are separately named. Do not infer phone state from the initial dump.

## Review and limits

Five deterministic policy tests cover idle renewal, independent/overlapping holds,
replaced contacts, teardown and old-Service hold identities. The new native test
adds real Pad contacts, composed cursor pixels, animation interruption, stationary
and remaining-finger holds, first-tap delivery and stale-release cases, but **has
not executed**. Exact fade timing/interruption, keyboard/status isolation,
physical takeover, Stop/rebuild/disconnect, controller/split holds, four rotations,
Fit and other OS/OEM remain current runtime gaps. Android's native hardware mouse
cursor is outside this feature. No full regression/performance pass is claimed.

[Selected current references](../../docs/ui-references/pointer-idle.json) record
private capture hashes and author review. Two modified input architecture diagram
blocks were rendered and inspected. The live index may advance in later releases;
this run's original capture/source bytes remain in its sealed archive. The other
four blocks were syntax-rendered only.
Fresh architecture/UI preflights and identities are in the
[change record](../../docs/changes/pointer-idle.md), not final publication approvals.
No new settings, setup/onboarding change, release, commit or push is requested.
The temporary UI hierarchy was removed and the phone controller reopened.

## Private evidence and reproduction

[Archive descriptor](archive.json) points to the verified ignored local-only ZIP;
source/build manifests, JUnit XML, tooling outcomes, raw blocked attempts,
manual driver/captures and preflights are retained until separately removed.
Nothing was uploaded; no raw screenshots/logs/archives are newly committed.
Hash verification proves byte consistency, not correctness or a passed test.

```powershell
./gradlew.bat spotlessApply qualityCheck
python tools/run_tool_tests.py --output testresults/0.18.0-pointer-idle/runs/20261113-host/host/tool-tests.json
python tools/run_device_checks.py --adb <sdk>/platform-tools/adb.exe --serial <xiaomi> --suite input --version 0.18.0-pointer-idle --timeout 480
python tools/coverage.py update
python tools/coverage.py check
python tools/check_requirements.py
python tools/check_documentation.py
python tools/check_privacy.py
python tools/evidence.py verify --archive artifacts/evidence/pointer-idle-20261113-final/evidence.zip --descriptor testresults/0.18.0-pointer-idle/archive.json
```

The device command was blocked at test-helper installation. The separate manual
driver, exact observed display IDs, coordinates, signing inputs and installed
hash check are in `20261113-review`; coordinates require fresh discovery on
another device. Use actual native acceptance after installation is allowed.
