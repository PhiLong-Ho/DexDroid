# Kotlin migration: phase 3 checkpoint

Scope: eight shell/Binder/input owners converted; **31/82 maintained sources**
now Kotlin. Product `360bd4c0...ff7bc`, final Java harness `c3cc21b8...e9cdb`.
Full hashes and copied sources are bound in `phase-3-teardown-inputs`. No physical
phone was used. Final hardware, upgrade and release acceptance remain pending.

| Configuration | Selected passing suites |
| --- | --- |
| API30 AOSP | input (`20261018T101356.779621Z`), legacy-navigation (`20261018T102108.245655Z`), keyboard (`20261018T102122.540445Z`), child (`20261018T102215.066572Z`), idle (`20261018T102238.640850Z`), split (`20261018T102323.285956Z`), profiles (`20261018T103020.277534Z`) |
| API36 host | input (`20261018T103232.301912Z`), keyboard (`20261018T103304.795276Z`), child (`20261018T103359.296323Z`), idle (`20261018T103423.692804Z`), split (`20261018T103515.529075Z`), profiles (`20261018T103847.542302Z`) |
| API37 software, experimental | input (`20261018T104019.885752Z`), keyboard (`20261018T104107.004608Z`), idle (`20261018T104207.667989Z`), profiles (`20261018T104304.131129Z`) |

These are 17 selected suite outcomes, including one source-bound API30 input reuse
from the e1ba4 test APK. Its product bytes and all app sources except the unused
LegacyNavigationAcceptance match the final pair; the private reuse manifest checks
111 paths. All downstream suites were rerun. API37 uses its recorded software
configuration; it is experimental, not general compatibility certification.

Host: final `phase-3-teardown-quality` passes compile/format/lint and **148 JVM
cases**. Bytecode comparison passes **12 named owners/48 original non-private
descriptors and static shapes**. The narrowly reviewed inherited lint baseline
relocates ActivityThread and records the existing InputManagerGlobal lookup;
no blanket baseline regeneration occurred. Python tooling reuses 169 passes/one
platform skip, with all24 tracked Python files byte-identical to the sealed phase2
tree. Coverage, documentation and privacy validation are rerun independently.

Three native failures remain explicit:

- `100545`: navigation-family broadcast overwrote child observation. Fixed the
  receiver's action/UID/family admission, preserving wrong-placement failures.
  Five delivered metadata probes plus the native child sequence pass separately.
- `101639`: legacy suite left a Child on its owned task, so split correctly
  restored it. Added validated native success-path Up/root Back and exact task
  absence, with independent teardown and primary-failure preservation. The
  corrected downstream sequence/full split passes; teardown error suppression
  is source-reviewed, not fault-injected.
- `102737`: AOSP first-Controls profile MOVE ANR after phone save/rename/dismiss.
  Its later scenarios are unrun in that attempt. The matched retained Java-model
  control is `094524` (PID3324, not older preview PID2745); the prior stage wording
  was incomplete. Current confirmation `103020` passes all196 labels and the
  later tail. Root cause remains unknown; no platform-only or no-lock claim.

Fresh approach reviews and exact scope corrections are recorded in the
[migration plan](../../../../docs/changes/kotlin-migration.md). The bounded API30
failure obligations remain for final ordinary emulator/hardware/release reviews.
No assertions, injection modes, timeouts, renderer properties or product behavior
were changed to obtain the profile confirmation.

Bounded visual inspection covered current API30 phone/monitor Layout, rotation3
pane output and top/bottom output; it is not whole-catalog/golden/physical approval.
New verification diagrams were rendered/inspected. The split timing sample is
100 injected DOWN dispatches after10warmups, p50 **52ms**/p95 **72ms**; it excludes
rendering, broadcast/await and physical finger latency, with no improvement claim.

Raw commands, source/APK bindings, all selected attempts, recovered captures,
controls, ABI/JVM results and inspected diagrams stay in the verified private
[archive descriptor](archive.json). Local-only availability: retain until owner
removal; not uploaded/public and no Actions retention claim. Archive integrity
proves retained bytes, not acceptance. Final all-suite/focused/setup/native Chrome
Stop, full render, production exclusion and in-place hardware gates remain.
