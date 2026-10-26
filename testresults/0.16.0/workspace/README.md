# 0.16.0 saved-workspace acceptance

Xiaomi 14, Android 16 / HyperOS 3.0.304.0, real 1920×1200 monitor. Raw device
captures, dumps and development binaries remain private local. These compact
records do not imply upload or universal Android/OEM compatibility.

| Scope | Original run | Outcome | Assertions | Seconds |
| --- | --- | --- | --- | --- |
| Final workspace build | `20261026T171832.601816Z` | Pass | 522 | 88.555 |
| Phone UI | `20261026T155650.838442Z` | Pass, reused | 475 | 32.884 |
| Monitor UI | `20261026T172011.361030Z` | Pass | 2434 | 104.311 |
| Split navigation | `20261026T160031.396839Z` | Pass, reused | 1149 | 87.083 |
| Audio | `20261026T160337.442954Z` | Pass, reused | 186 | 19.168 |

Assertions are not independent scenario counts or coverage percentages. The final
workspace run covers Single/Split, four rotations, swap/ratio/maximization, empty
panes, physical touch/Back, private-child canonicalization, unavailable-app
preflight, status notice retention/expiry, Fit preview, cancellation, cleanup retry
handoff and queued-launch guards. Native metadata/task checks and real input
outcomes supplement service-state assertions.

The final workspace and monitor suites use the final development binaries. Phone
UI, split-navigation and audio retain their original source/APK bindings. Later
changes cover setup copy/labels, eligibility recovery, ordinary Single launch
stability and fresh owned-display capture/reconciliation. Shared UI styles, pane
input/geometry and audio algorithms remain unchanged; final workspace covers the
new capture boundaries and four-rotation Single/Split input, while final monitor
acceptance checks affected ordinary launch/input/empty controls. Two overlapping
rejections, unknown native capture/retry and delayed Save followed by a same-app
launch or Stop/recreate preserve the previous shortcut. Historical Home smoke is
source-bound entry/pinning evidence, not a new final-process timing measurement.
The older UI/split test artifact predates an isolated AudioWorker assertion change
that those suite flags never execute. See [structured bindings](summary.md).

The actual Home page-2 **Restore workspace** icon reopened a stopped app process
and placed saved stock Settings on an owned virtual display, keeping the controller
on the phone. Its Home screen shortcuts permission was initially Deny and was
allowed manually while troubleshooting. The icon was on page 2; this test did not
isolate the initial absence to permission versus page location. No confirmation/
cancel dialog appeared on the tested path. Product code does not automate that
permission change. The pin-request screen and 12 final
workspace renders were independently approved by `/root/workspace_render_review`:
pin request at 2026-10-26 16:22:02 UTC; renewed true four-rotation menus at
17:21:04 UTC. The earlier 408-assertion run established only 0° restoration;
its four-rotation claim is withdrawn. Requested rotation is now checked independently
before capture/after restore, alongside native app-window dimensions and input.

Host checks pass: 234 JVM cases; 210 tooling cases plus one Windows symlink-privilege
skip; formatting, lint, requirement/coverage/documentation/privacy and Ruff checks.
The host ZIP is a **private review bundle**, including development binaries and
diagram renders. CI generates its own separate privacy-guarded public host archive
and release report.

Verified private bundles:

- [Host descriptor](host/archive.json): `e077f22319a2af38332173040085bf3363d9b51b4b69ad635ba544b2168ec3b3`.
- [Device descriptor](device/archive.json): `dbb6f7704eb1aba728097e1bb27452b4547b8bb6b0f12eb9bb51f7b0b2e0edf8`.

Both archives are local-only at the descriptor paths, retained until the owner
deletes them. Reviewers can verify/extract them locally with `tools/evidence.py`.
Original failures, install restrictions, timeout, rejected refill setup, unavailable
fixture setup and runner-label misclassifications remain inside the device archive.
Review findings are recorded in the change document. Rejected outcomes have not
been rewritten as passes.

Other OS/OEM/launchers, monitor-side pin handoff, confirmation cancellation,
offscreen Fit-bottom pixels, assistive technology, enlarged fonts, full keyboard/
peripheral combinations, non-default audio across restore and acoustic/ordinary
frame-input latency remain gaps. Nine controlled activation samples are descriptive
only; see summary for timer boundary and values. App-internal state is outside scope.

Source/requirement narrative: [change record](../../../docs/changes/saved-launch-shortcut.md).
