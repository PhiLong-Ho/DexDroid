# Centered reserved taskbar: development verification

Baseline `53bf44a5bb9e04433d143feaa593137ed863f1e8`; uncommitted feature branch
`feat/phone-panel-power-prototype`, including the prior phone-screen increment.
Version0.20.0/build53 Debug on physical Xiaomi14 Android16, 1920×1200 monitor.
No commit, push, production release or final-candidate approval is claimed.

Current native product APK SHA256
`9c21987af69f6ad85d3cce74aa0fa4281ccb283c690b916476b3349731b73641`.
The runner records every installed product/test APK hash. Taskbar, input and UI
evidence predates test-only phone-guard ownership/target and monitor asynchronous
toggle-readiness corrections. Production-source payload stays identical; test APK
hashes differ and are not presented as one binary. Exact current source hashes,
tested APK and host XML/logs are under private member `taskbar-completed/`.

| Check | Outcome / private archive member |
| --- | --- |
| `gradlew spotlessApply qualityCheck` | Pass; 317 JVM tests, no failures/errors/skips; `taskbar-completed/quality-completed.log`, `jvm/` |
| Python tooling | 211 tests, 210 pass and one Windows skip; current log in `taskbar-completed/` |
| `--suite taskbar` | Pass; `20261113T165823.605192Z/` |
| `--suite input` | Pass; `20261113T170355.046315Z/` |
| `--suite ui` | Pass; `20261113T170459.771890Z/` |
| `--suite phone-screen` | Pass after fixture corrections; `20261113T170837.109188Z/` |
| `--suite monitor` | Pass after async-toggle readiness correction; `20261113T170951.401178Z/` |

Focused taskbar scope: compact centered group with8dp gap and48dp targets in four
rotations and separate Fit; exact native display/buffer content sizes; real lower
app contact; blank-strip/menu exclusion; shell access while app touch is Off;
native mouse hover/wheel and phone-pointer actions; shared center preference and
restart; both empty split directions and native dimensions; preview rejection and
revert. Assertion lines are not scenario counts or performance statistics.

Retained failed attempts include phone Switch root/screen-coordinate readiness,
an assertion on hidden idle view geometry, a preflight build/install hash mismatch,
an unowned phone-guard fixture and monitor input issued before the new asynchronous
navigation resize settled. The corrected native outcomes are separate runs.
One repeated `--suite` command selected only its last value; it is not counted as
four regressions. Historical phone-screen/raw runs co-sealed in this bundle retain
their own source scope; they are not new taskbar passes.

Author reviewed selected current native phone/settings and monitor frames,
rotation-normalized taskbar crops and five rendered source-linked architecture
diagrams. `docs/ui-references/monitor-taskbar.json` binds source and capture hashes.
Additional byte-level checks find white taskbar icon/text pixels in each retained
Fit/split frame; inconsistent viewer image ordering did not establish a product
rendering bug. No human pixel golden or full accessibility/performance approval.

Open gaps: held-input boundary crossing, failure-injected native resize rollback,
occupied split native input, native IME/focus/insets, phone-off taskbar restore as
one combined interaction, large fonts/TalkBack, other OS/ROM including Android11
and measured battery/latency. No full compatibility matrix or release regression.

The [archive descriptor](archive.json) records verified ZIP/member hashes and
local availability. Private local-only retention until separately removed; no raw
logs/screenshots/APK were uploaded or newly added to Git. A local descriptor is
not upload proof. The taskbar is enabled for review on the installed development
build; center-icon choice remains unchanged. Test package removed; controller
reopened and both physical panels confirmed On in retained final diagnostics.
