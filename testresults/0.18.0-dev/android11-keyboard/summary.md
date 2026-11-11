# Android 11 keyboard fallback acceptance

**All four requested suites passed** on the Black Shark4/JoyUI Android11,
API30, real1920×1200 touch monitor. Installed in place:0.18.0-dev/build50.
No production code, permissions or persistent developer settings changed.

| Suite | Outcome | Duration | Run |
| --- | --- | --- | --- |
| `native-ime` | PASS | 6.172s | `20261111T190837.091503Z` |
| `keyboard` | PASS | 87.797s | `20261111T190845.063630Z` |
| `input` | PASS | 33.469s | `20261111T191014.074304Z` |
| `legacy-navigation` | PASS | 14.469s | `20261111T191048.838988Z` |

The fallback test exercises On/unavailable on both Settings surfaces, a real
monitor Off touch, phone On touch, unchanged desktop ID/focus eligibility and a
real monitor contact reaching a separate-UID editor while the option remains On.
The reason is explicitly LEGACY_ANDROID; Android11 never acquires the native
keyboard focus lease. Local-keyboard/input/navigation suites then exercise their
existing four-rotation, Fit, editing, gesture, Single/Split and lifecycle scenarios.
Their prior preference is restored; do not claim their whole matrix ran with On.
Actual commands and configuration are retained in each run.json.

Native keyboard placement remains **unavailable on Android11**. The immediate
editor observation reported no IME window, and the inspected phone capture has
no visible keyboard. This does not establish successful general phone-IME routing
or its absence at later times. The working local Apps-search keyboard is a
separate verified feature. No Chrome/personal-app keyboard claim is made.

The author inspected both On/unavailable Settings captures: complete readable
row, separate switch and no title/status collision. Phone is portrait; the monitor
uses the saved1200×1920/320DPI portrait workspace on its landscape signal. These
are scoped observations, not a new human/expert golden approval or all-state proof.

Host preparation: build14s and qualityCheck21s pass with Gradle cache reuse;
requirement/coverage/documentation/privacy guards pass. Source inventory differs
from the previous Android16 evidence only in NativeImeAcceptance.kt. Android16
is not retested here; its earlier outcomes remain separately bound historical runs.
All other OS/OEM/IME, physical keyboards/mice and broader race/performance
scenarios remain unverified. No assertion count is a scenario pass rate.

The development app remains installed. The separate acceptance-test package was
removed and DexDroid reopened on the phone. Existing user app data was retained.
No release, commit or push occurred; earlier default/application decisions and
publication review gates are unaffected.

## Private evidence

[Verified archive descriptor](archive.json), SHA256
`08f58524304eb3304207243bc45203baa5fa12330302267653a5711e0909d58a`. Local-only, no upload or remote retention claim;
available until manual removal. Original run folders remain under ignored
testresults/0.18.0-dev/runs. Selected byte-identical copies were packed for this
follow-up, with APK/source fingerprints and historical preparation. Screenshots,
device dumps and logs must not enter public host/release evidence.
