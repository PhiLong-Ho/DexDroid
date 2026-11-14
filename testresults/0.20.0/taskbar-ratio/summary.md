# Fixed4% taskbar: development verification

User-selected final sizing: round(4% of the full logical output's shorter edge),
independent of app DPI, with no override. Square targets equal bar height, glyphs
use half-height and gap one sixth, rounded to pixels. Existing shell/viewport
ownership and native resize transactions remain; this explicitly supersedes the
earlier48dp taskbar minimum. Fresh UI and architecture experts approved the
approach; no release or final-candidate approval is claimed.

Physical Xiaomi14 Android16, real1920×1200 monitor. Focused taskbar run
`20261114T022621.471520Z` passes: four rotations, separate Fit, native content
dimensions, lower app input, shell touch/mouse/phone pointer, shared center
preference and both empty split directions, preview/revert and no click-through.
Explicit landscape240/320/120DPI checks measure equal bar/target/glyph/group pixel
dimensions, including low-DPI vector upscaling. At1920×1200 the bar is48px;
at1920×1080 it rounds to43px. This is scoped acceptance, not a full OS/suite matrix.

`qualityCheck` passes318 JVM tests, zero failures/errors/skips. Current metadata,
logs, JUnit XML and source/build fingerprints are sealed under `host/`. Baseline
`53bf44a5bb9e04433d143feaa593137ed863f1e8`, uncommitted feature branch. Product APK
SHA256 `fcd28277a380e54a5acbfc863a81afe803117e623a52fbef61f61bc705a2be92`.
The first host build passed, then installation lost wireless ADB; mDNS rediscovery
and reconnect restored access. No native test outcome is claimed for that attempt.
The final host build also includes explicit landscape orientation for DPI checks.

Author inspected current landscape DPI comparison and four-rotation derivative;
`docs/ui-references/monitor-taskbar.json` binds these to original-frame hashes and
source inputs. No human golden or comprehensive accessibility/performance approval.
No fresh unrelated broad device or Python tooling suites; prior results remain
historical reuse documented in the [preceding summary](../taskbar/summary.md).
Current requirement/coverage/documentation/privacy guards are separate checks.

Remaining gaps include held-contact boundary crossing, injected resize failures,
occupied split native input, native IME/focus/insets, phone-off taskbar restore as
one combined interaction, other OS/ROM including Android11, and performance.
The exact ratio can produce targets smaller than48dp; no accessibility-size
override is shipped, as requested. Other menu controls retain normal sizing.

[Archive descriptor](archive.json): current-run-only private local bundle, retained
until separately removed. No raw device evidence uploaded or newly added to Git.
Development build installed, test package removed and controller reopened. No
commit, push or release was requested/performed.
