# Native monitor keyboard — development acceptance

Development0.18.0-dev/build50 on `feature/native-monitor-keyboard`, based on
`5fdd642001e04a663f58cc6e68d288080299b36b`; dirty source inventory and tested APKs
are retained in the private archive. **Not released, committed or pushed.**
Human initial/upgrade default and apply-on-next-Start decision remains pending
review `R-1794419209-0-69509750718464`. Two fresh final candidate reviews and any
release architecture attestation have not been performed; earlier approach/UI
reviews do not substitute for them.

## Current outcomes

Xiaomi14/Android16, real1920×1200 touch monitor, selected English Gboard,
Shizuku existing authorization; no new permission/default-IME/developer setting.

| Check | Outcome | Exact run |
| --- | --- | --- |
| Native IME and child/handoff regression | PASS,102.875s | `20261111T182137.864755Z` |
| Existing local search keyboard | PASS,56.969s | `20261111T182322.446341Z` |
| Existing input regression | PASS,33.954s | `20261111T182420.742643Z` |
| Focused split/modal iteration | PASS | `20261111T182043.529757Z` |
| qualityCheck | PASS,25s;287 JVM cases,0failures/errors/skips | `20261111T182718Z-host/qualityCheck.txt` and `junit/` |
| Tooling |203passed/8skipped of211,18.105s | `20261111T182718Z-host/tooling.txt` |

Native acceptance uses actual selected-IME keys and one physical host contact per
tap, exact separate-UID editor changes, consecutive typing/Delete, all4rotations,
1920×1080@240 Fit, left/right/left/right focus, native first/second Back, actual
shared Settings switches, pre-contact owned-window focus and global B/F12
isolation, no outside clickthrough and close/retap. Its nested existing suite
checks private child navigation, phone handoff, remaining-app input and app exit.
Assertion counts are not scenario denominators or a universal pass rate.

Final native app APK SHA256: `8865536be42533560371feee86b93218edb6ba47e1559060f97ee7b0493782f1`.
Current `20261111T182718Z-host/tested-source.json` matches all captured app source
and build inputs. `apk-inputs.json` binds signature-only development packaging to
the original built payload. Current full native and input/keyboard runs retain
their own installed-APK hashes. Tooling output is reused from18:11 because its
Python sources/harness are unchanged; it is not a new18:27 execution. Historical
trials use their own run metadata, not the final source inventory.

Requirement, coverage, standalone-fixture definitions, documentation, privacy
path/signature guard, Ruff and diff whitespace checks pass. Mapping/static
guards do not certify native input, visual states or privacy semantics.

## Render and architecture review

`/root/native_ime_ui_preflight` approves the pictured On/configured Settings row
from final full-native run: entire readable row, separate switch, clean wrapping,
unobscured Close on portrait phone and landscape split monitor. The native IME
is hidden behind the focused menu in these final captures. This is expert scoped
rendered review, not a human-approved golden or default decision. The master
reference catalog is [native-monitor-keyboard.json](../../../docs/ui-references/native-monitor-keyboard.json).

`/root/native_ime_arch_preflight` and subsequent `/root/native_back_arch` approach
reviews cover per-display policy leases/bounded workers and creation-time focus.
The latter inspected actual owned-Activity modal handoff and its successful
pre-contact focus/key evidence. Seven current system/feature diagrams rendered;
changed overview/modal sequence and earlier unchanged diagrams were inspected.
These are approach approvals, not release/final-candidate attestations.

## Limits and retained failures

- Android11 hardware, API31–35/37+, other OEMs/IMEs/Presentation hosts, physical
  Bluetooth/peripherals, large fonts and unpictured pending/unavailable/Off states
  remain unverified. Android11 deliberately retains the original routing/flags.
- Ready-menu input ownership is verified, including a291ms menu-ready sample.
  The asynchronous gesture-to-ready interval, rejected/delayed Activity starts and
  broader Close/Stop/replacement races are not certified.
- Earlier failures are retained: own-focus pane switching; stale Gboard animation/
  virtual-node geometry and suggestion labels; native contact test setup; nested
  startActivitySync reuse; nonempty handoff fixture setup. The final matcher
  refreshes candidates and chooses compact English-Gboard key targets; it never
  substitutes injected text, accessibility clicks or retried contacts.
- Local Hide miss `175340` remains unexplained; subsequent full local-keyboard
  passes do not establish that every intermittent contact failure is eliminated.
- 25 native-key harness samples:136–172ms, nearest-rank p50=149ms/p95=160ms. These
  include approximately80ms synthetic contact, IPC and40ms observer polling.
  They are not physical-finger/touch-to-photon latency or a comparison benchmark.

The development build remains installed with its explicitly enabled trial
preference. The separate `dev.dexdroid.test` package was removed after testing,
and DexDroid was left open. No personal app was launched. This is not a production
installation or an official release; debug-only editor code is absent from the
release source set.

## Private evidence

[Descriptor](archive.json): verified local-only archive,945files,30,246,729bytes.
SHA256 `a0fbdb01afd3f94cd7dafd5670891fa003c3eca9bc533c98281317677dbc5a6f`. Raw originals remain under ignored versioned
runs, including failed attempts. Current host payload and source/build bindings
are in `20261111T182718Z-host/`. No raw device material or ZIP is committed or
uploaded. Device dumps/captures are private and must not be included in public
host/release evidence. Local availability lasts until manual removal; no remote
retention or community access is claimed.
