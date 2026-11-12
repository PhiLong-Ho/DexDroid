# Release 0.19.0 preparation

Physical Xiaomi14 `23127PN0CG`, Android16/API36 HyperOS
`OS3.0.305.0.WNCMIXM`,1920×1200 monitor, single landscape/UI DPI320; portrait
phone at its existing font/density. Tested development0.19.0/code52 installed
in place, preserving data. Raw runs UTC2026-11-12, Singapore2026-11-13.
Dirty baseline `c05649ce804f077700db50676827b48a812ac321`; archive source/build
hashes identify the tested inputs. Existing unrelated local policy/promotion
drafts are excluded from the candidate.

| Check | Actual outcome |
| --- | --- |
| `spotlessApply qualityCheck` | Pass14s;96 tasks,35 executed/61 up-to-date |
| JVM |295 passed, zero failures/errors/skips |
| Final tooling |210 passed/one Windows skip;211 total,18.257s with Pillow present |
| Ruff, fixture definitions, coverage/requirements/docs/privacy | Pass; baseline unchanged |
| Native input | Pass59.437s, run `20261112T173230.100641Z` |
| Native phone UI | Pass37.250s, run `20261112T173407.420204Z` |
| Native monitor | Pass125.797s, run `20261112T173446.315788Z` |
| Current manual phone/monitor checks | Passed within scopes below |

Combined local host505/506 reported cases passed; skipped counts are not passes.
Earlier tooling runs without Pillow had eight skips, so their denominators differ.
The initial Ruff Python-module launcher could not locate its executable; pinned
`uv run ... ruff` check/format passed. CI Linux runs its own current host outcomes,
reports and private-free release archive; these local numbers are not CI claims.

Installed development app SHA-256:
`558e2189436eb6af211a29633d2bcb10b9420777a4e43dbe9f2283dc5b687df1`.
Installed base bytes were verified against the final signed build. Development
signing used the existing certificate with equal non-signature payloads; this is
not a signed production-artifact or publication claim. Final app/test/source inputs
and JUnit/tooling outcomes are in `release-preparation` inside the archive.

## Current real-device manual scope

- `manual-settings-final`: installed version/build, related `developer` term,
  Turn off Developer Mode destination, child Up retains query, Clear/no-results,
  Close/fresh-entry reset. No debugging shutdown was invoked.
- `manual-guide`: all five gesture labels/three notes, native 48dp Close target,
  Close/native phone Back/outside dismissal without toggling underlying status,
  and reopen. Guide is passive and phone-only.
- `manual-monitor`: top-edge Controls, Settings, virtual-keyboard search for Audio,
  Audio child Up retains query, Clear/no-results and outside/fresh-entry reset.
  No audio route changed. Current local keyboard is pictured; no physical keyboard.
- `manual-pointer`: synthetic built-in input fixture launched through phone Apps;
  cursor pixels18464→18680→18464 distinguish wake/idle. A stationary raw DOWN
  stays visible beyond5.7s; release restarts idle; the first wake tap restores the
  pointer and the native fixture records exactly one tap after zero earlier taps.

Author inspected current phone Settings/help, monitor controls/search/no-results,
and hidden/wake synthetic frames. Current references:
[search](../../docs/ui-references/settings-search.json),
[pointer](../../docs/ui-references/pointer-idle.json),
[help](../../docs/ui-references/touchpad-guide.json).
No screenshots/logs/dumps are newly committed or uploaded. Exact manual drivers,
commands, observations and PNG/XML originals remain private. Temporary device
hierarchies were removed. Initial dump/focus and empty-field assumptions are
retained separately: UIAutomator initially selected the external window, and it
reports an empty EditText's hint as its text. Final checks use the correct native
semantics; product/test assertions were not weakened.

## Current native verification and limits

The helper was installed after observing/confirming its timed Install via USB
dialog. No remembered choice/global security switch changed. The first attempt
`20261112T173128.977593Z` reported Process crashed before test results; Android
logged pid/startSeq problems. Explicit target force-stop before retry and each
following suite allowed execution. Exact initial cause is unestablished; raw
failure/installer evidence is retained.

Input covers actual rotation/Fit mapping, physical takeover and background/destroy
behavior, plus new opacity/fade interruption, key/status isolation, stationary and
remaining-finger holds, first-tap down/up delivery and stale release. Pointer-specific
checks run after the rotation loop, not at every rotation/Fit combination. Phone UI
covers help labels/plain48dp Close/input isolation, Settings navigation/keyboard,
and six native User guide wrapping combinations (240/360dp × font1.0/1.3/2.0),
not compact-Help or whole-app font certification. Monitor covers four rotations and
separate Fit, search/local keys and menu/guide readability. PASS-line counts are
repeated assertions, not independent test cases or performance measurements.
Same final app/test APK hashes bind all three runs.

Author inspected native phone Help/keyboard, pointer hidden/wake and sixteen
monitor search/guide states in two normalized cropped derivatives, alongside the
earlier selected current full manual frames. Originals/transformation records are
retained. Guide crops emphasize content, not every header boundary. No human
pixel golden, physical-finger latency or performance distribution claim.
Controller/split held-idle, teardown-during-fade races, compact Help enlarged fonts/
TalkBack and other OS/OEM remain partial. System hardware cursor is platform-owned.

Earlier [menu](../0.18.0-menu-readability/summary.md),
[search](../0.18.0-settings-search/summary.md),
[pointer](../0.18.0-pointer-idle/summary.md) and
[help](../0.18.0-touchpad-guide/summary.md) runs remain historical scoped evidence.
`historical-bindings.json` lists source differences; old full input/UI/rotation
runs are not recast as final passes. Prior workspace cold restore reached the
task/display/axes but timed out waiting for input admission before injection;
cause remains unresolved/unretested. No full workspace-regression claim.

[Archive descriptor](archive.json) identifies the verified ignored local-only ZIP,
retained until separately removed. It contains current host/manual sources,
outcomes, earlier install/process failure and current native passes; no remote retention guarantee. Hash verification
proves byte consistency, not correctness. Fresh release architecture and final
reviews are separate prerequisites; see [release scope](../../docs/changes/release-0.19.0.md).

```powershell
./gradlew.bat spotlessApply qualityCheck
uv run --no-project --python <python> --with-requirements tools/requirements-dev.txt ruff check tools
uv run --no-project --python <python> --with-requirements tools/requirements-dev.txt ruff format --check tools
uv run --no-project --python <python> --with-requirements tools/requirements-dev.txt python tools/run_tool_tests.py --output testresults/0.19.0/runs/final-host/host/tool-tests.json
python tools/check_fixture_runtime.py
python tools/coverage.py check
python tools/check_requirements.py
python tools/check_documentation.py
python tools/check_privacy.py
python tools/evidence.py verify --archive artifacts/evidence/release-0.19.0-native-final/evidence.zip --descriptor testresults/0.19.0/archive.json
```
