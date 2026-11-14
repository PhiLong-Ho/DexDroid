# 0.20.0 development verification: phone screen power

This is local implementation evidence, not a published release. Baseline
`53bf44a5bb9e04433d143feaa593137ed863f1e8`, uncommitted feature branch
`feat/phone-panel-power-prototype`; unrelated existing workflow/promotion drafts
were preserved. Current source/build fingerprints and the tested APK are private
archive members under `completed-implementation/`.

Physical Xiaomi14, Android16, 1920×1200 external monitor. Installed development
APK SHA256 `6fba86dd1bc35eb89b2ea1c5adba46728623ea6da1850bab7c24bc98bc0581d9`,
version0.20.0/build53 Debug. Current built APK's non-signature payload matches the
installed tested APK. Native runner manifests record individual test APK hashes;
test-only label-count/optional-header assertion corrections explain their changes.
Production-source evidence is reused explicitly, not asserted to share one test APK.

| Current check | Outcome / private archive member |
| --- | --- |
| `gradlew qualityCheck` | Pass; 313 JVM tests, zero failures/errors/skips; `completed-implementation/quality-completed.log` and `jvm/` |
| Python tooling discovery | 211 tests: 210 pass, one Windows skip; `completed-implementation/tool-tests-final.log` |
| `run_device_checks.py --suite phone-screen` | Pass, 38.656s; `20261113T160208.835648Z/` |
| `--suite input` | Pass; `20261113T160249.948574Z/` |
| `--suite ui` | Pass; `20261113T160639.921787Z/` |
| `--suite monitor` | Pass; `20261113T160921.963448Z/` |
| Deliberate helper termination during verified OFF | Own postconditions pass, first On observation0.141s; `lifecycle-20261113T161137Z-helper/` |
| Deliberate app force-stop during verified OFF | Own postconditions pass, first On observation0.203s; `lifecycle-20261113T161149Z-app/` |
| Ruff, requirements, generated coverage, privacy, whitespace | Pass; final validation log in `completed-implementation/` |
| Documentation/archive integrity | Pass after sealing: 196 Markdown documents checked; `.local/phone-screen-implementation/documentation-completed.log` (local follow-up outside sealed bundle) |

Assertion-line counts are not test/scenario denominators. The two termination
fixtures intentionally interrupt instrumentation: their suite output is not a
suite pass. Both require internal Off/external On before interruption and both On
afterward, and byte-verify preference restoration. Timing has one sample per case.

Focused coverage: explicit native monitor restore in four rotations, separate Fit
1920×1080/UI240, phone input consumption, no late OFF after readiness cancellation,
guard loss, Stop, background and hidden-preference recovery. Native phone/monitor
regressions cover their existing selected scope. No Android11–17 matrix was run.

Retained failures: test Switch return-value assertion, global keyboard-focus proxy
rejecting OFF, default-display injection attempted through the external-only fixture,
stale settings count and hidden optional icon geometry. These are separate failed
run folders, not relabeled passes. Python's early stale-coverage failure and one
viewer startup timing failure also remain in original host logs; final tooling run
passes. The earlier prototype's failed physical Power/double-tap observations
remain indexed under0.19.0 and are not current product acceptance.

Author inspected current phone header and monitor Settings/Fit, the original-frame
four-rotation monitor QA comparison and four rendered architecture diagrams.
`docs/ui-references/phone-screen-power.json` binds the selected captures and source.
This is not human golden approval or comprehensive accessibility/performance proof.

Open gaps: real cable unplug/reconnect, Shizuku broker death, compositor stalls,
other OS/ROM including Android11, keyboard/large-font/TalkBack combinations,
sustained battery/latency and exhaustive dimension crosses. A native compositor
stall or simultaneous termination of all shell processes precludes a finite
restoration guarantee. Power retains normal Android lock behavior; no double tap.

Evidence is private local-only, no upload/publication. [Archive descriptor](archive.json)
contains its location/hash; retain locally until separately removed. Verify using
`tools/evidence.py verify`. Raw screenshots/dumps/APKs are ignored and not added
to Git. Final device state: test package removed, controller reopened, internal
panel On. No commit/push/final review/release approval is claimed.
