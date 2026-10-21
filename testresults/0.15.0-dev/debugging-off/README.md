# Debugging-off development acceptance

Uncommitted branch `feature/debugging-off` on released v0.14.0 / 2e9c5af.
Build 0.15.0-dev / 41 is a development build, not a new published release.

## Current generic recovery UI (2026-10-21)

Android15 API35 emulator,1920x1200 simulated monitor, run
`20261021T052414.494285Z`:222 native assertions/22.957s pass, Android result-1,
collection/controller cleanup pass. Standard debug-key product
`15eec66d3c62e116c0ebaa7ce630db83a2e1f60a6de4b7d6223de09fff77b525`
and test `6fc3fea3cea8c48055906098f82eddad34c753e0c6e6165da54dd2a9c34c2c8f`.
Checks cover generic recovery collapsed/expanded/collapsed state, accessible
expansion status, unchanged receipts/session and intercepted main Settings intent
on phone and all4monitor rotations, plus the existing controlled shutdown cases.
The Settings Activity is blocked by instrumentation: this does not certify actual
OEM Settings navigation. No real flag writes or banking apps in this run.

UI reviewer `/root/debugging_generic_recovery_ui` inspected/approved all12 new
renders at2026-10-21T05:25:23Z within pictured emulator scope. The
[current reference catalog](../../../docs/ui-references/debugging-off.json) binds
these sources/images; older catalogs/archives are historical. [Native archive](generic-recovery/archive.json)
contains30files, SHA4b743008c11fe369f57aeff062f1a5a2b2a36f08778c77e7ed5549c2b72b0784,
verified private local, no upload. Current qualityCheck20s passes216JVM outcomes;
tooling211tests/19.560s has210passes/1Windows symlink privilege skip.
[Current host archive](generic-recovery-host/archive.json) retains33files,
SHAd91a41fc928c85adcbdd5deb18e937cf0f92610a15c58303a3ddd030f6a8223f.
Final qualityCheck2s reused unchanged build/test outcomes; requirements, coverage,
fixture-runtime, documentation, privacy and Ruff gates pass.
The initial production-key emulator update was rejected for certificate mismatch;
no uninstall/reset was used. The emulator was then updated normally with its
existing debug signer. Physical phone has not received the new generic UI while
the user's private banking check is pending. No new physical/bank pass is claimed.
Fit/large fonts, pending/launch-error/UserGuide renders, full accessibility and
performance remain unverified. Unchanged audio hardware outcomes below are reused
with their original source/build scope. No full unrelated regression was repeated.

## Historical Xiaomi follow-up (2026-10-21)

Earlier guidance development build `c35d226e...` / test `397d9250...` passed152
native assertions in24.358s (`20261021T031430.668248Z`) with collection/restoration
and Android result -1. New checks exercise the always-visible hidden-menu recovery
paragraph and scroll-bottom access on phone and monitor rotations0?3. Actual UI
reviewer `/root/debugging_hidden_options_ui` approved all12 current images at
2026-10-21T03:15:49Z; [current catalog](../../../docs/ui-references/debugging-off.json).
Scope excludes Fit/large fonts, pending/launch-exception renders, complete keyboard/
accessibility and rendered User guide. No measured performance certification.

[Current native archive](hidden-recovery/archive.json):29files, ZIP
`90fdd7832d5dc5c35c0f5b241e5d59f654c8ace21f3af07dd67c13de37799294`.
[Current host archive](hidden-recovery-host/archive.json):22files, ZIP
`99e8b3431812fb585094bf7ff87df02b19e8fa4e1347f6c5e89cdf68199e19d3`.
qualityCheck passed40s with216 JVM outcomes/zero failure/error/skip. These are
private local verified bundles; no upload. Prior unchanged audio/worker and
tooling evidence below is reused with its original build/run, not re-execution.

Official Play installations: HSBC Singapore5.0.0/code26100712 and DBS PayLah7.3.0/
code257. No bank login. With all three flags on, PayLah displayed its USB/Wireless
debugging restriction. HSBC terminated during startup (recorded SIGKILL9); cause
unconfirmed, no readable warning captured. A direct master-only write was observed
as developer0/USB1/wireless1 with adbd running; it does not establish the OEM
Settings UI switch cascade. The first full DexDroid attempt disconnected ADB and
lost Shizuku; the product persisted an honest unknown receipt. The detached shell
recorder remained at READY and produced no after/bank files, so bank acceptance
and exact historical off values were not verified. User restored access manually.
A second current-build full attempt is now awaiting the user's bank observation
while debugging remains off. Raw real-app evidence stays private local under
`.local/debugging-off/bank-check/`; no current bank pass is claimed.

The following earlier controlled outcomes are retained historical evidence.

## Earlier controlled outcomes

- Xiaomi 14 / Android15, physical 1920x1200 monitor: focused non-destructive
  debugging suite `20261020T190753.416345Z` passed128 assertions in19.616s,
  Android result -1; collection and controller restoration passed.
- Covers phone Cancel/Up, monitor Cancel in four rotations, actual single/split
  cleanup, controlled unconfirmed focus/cleanup, timeout and early-timeout
  persistence ordering, duplicate confirm/Start and stopped phone result route.
  Settings transport is a double: **no real debugging flags were changed**.
- Current product audio regression `20261020T190832.070018Z` passed173 assertions
  in18.508s, Android result -1; separate-UID silent PCM routing and audio-worker
  barriers passed. This does not establish acoustic/Bluetooth behavior.
- qualityCheck passed9s;216 JVM outcomes, zero failure/error/skip. Tooling passed
  210/211 in18.137s with one Windows symlink-privilege skip. Some Gradle outcomes
  are reused up-to-date results. Requirement/coverage/fixture/docs/privacy/Ruff
  checks passed; raw local host logs remain under the dashboard log directory.
- Product SHA256 `b8d1b5852a264413bdfcb95805408ee3a3dbd59f1c1f7967b093f31c81b114a3`;
  test `2b5cceae0247baffd8328f23e13ae45b601dc888644869205dc4dde9672616b2`.
  Both bundles retain current source fingerprints and signed/unsigned payload proof.

## Availability and review

[Device descriptor](device/archive.json) binds24files, ZIP760df407fb83ad419d9afe508385470ea36600ea52a65433939a277bd97233a9.
[Audio descriptor](audio/archive.json) binds24files, ZIPd4350198156351d532b0ecb2a59d53f7bc06341dc7c5c61b7c24fd073334ee2b.
[Host descriptor](host/archive.json) binds23files, ZIP6c3c06419b051e80d3b86f75ddd4121195b7b502e5ff9d50a78d7c353dbeca1e;
raw quality/tooling/JUnit and source/signing bindings are retained there.
Archives are verified private local under `artifacts/evidence`, available until
removed by the owner; no public upload. Original runs remain untouched.

Actual UI reviewer `/root/debugging_off_render_review` approved all seven settled
current images, with no clipping/ghosting, within pictured scope. The
[reference catalog](../../../docs/ui-references/debugging-off.json) records literal
ZIP members and hashes. Earlier 250ms phone result captures were rejected for
transition ghosting;1000ms settling produced approved frames. This is capture
settling, not a measured product latency or pixel baseline comparison.

## Retained failures and gaps

- `20261020T190037.628799Z`: no test APK after local signer verification lacked
  JAVA_HOME; no native test execution. Fixed host environment before installing.
- `20261020T190421.506755Z`: controlled result test selected a stale pre-replacement
  dialog; changed it to await actual current laid-out enabled controls. Retained
  raw failure; subsequent complete affected suite passed.
- `20261020T190647.967361Z`: controller-only suite requires no monitor, but one was
  attached. Its no-monitor assertion rejected the configuration; not a product
  regression or a pass. No unplug campaign requested/run.
- Actual master Settings cascade, complete real switch readbacks, daemon
  shutdown and post-shutdown private no-login HSBC/PayLah outcomes remain unverified.
  Official bank installations and baseline observations are recorded above.
- Process death, every service/permission interruption, Fit/enlarged font/full
  input/keyboard combinations and other OEM/OS versions remain explicit gaps.
- No final candidate approvals, push or release of debugging-off yet. Audio
  v0.14.0 publication is a separate completed scope.
