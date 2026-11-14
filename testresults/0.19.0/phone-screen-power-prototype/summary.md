# Phone screen power feasibility — 2026-11-13

This is a post-release private experiment against installed DexDroid 0.19.0 (52),
not a feature included in that release or a production acceptance pass.
The [physical follow-up](../phone-screen-power-physical/summary.md) supersedes the
unverified physical-input scope below; this initial sealed archive stays unchanged.
Source baseline: `53bf44a5bb9e04433d143feaa593137ed863f1e8`; no app source or
maintained test changes. [Contract and review](../../../docs/changes/phone-screen-power.md),
[deferred requirement](../../../docs/specs/phone-screen-power.md): FR-044 / DX-PANEL-001.

Device: Xiaomi 14 / Android 16, physical USB-C 1920×1200 monitor, single idle
desktop, native monitor input and injected phone taps. Other rotations, Fit,
split, hardware input, other ROMs and Android 11 are unverified for this prototype.

| Observation | Outcome |
| --- | --- |
| Selective physical token, finite independent watchdog and host restore | SurfaceFlinger internal Off/external On; awake/unlocked maintained; then internal On |
| Fresh native monitor tap while phone power reported Off | Apps menu opens from inspected idle screen; resulting private frame inspected |
| Guarded double tap with panel On, then Off | Two consumed injected contacts, one WAKE; restored On, monitor On and unlocked |
| Physical double tap and Power | Unverified; manual trial timed out before Start and admitted no OFF |
| Cleanup | Temporary fixture uninstalled; production controller reopened, Awake and keyguard false |

Private raw commands/builds/metadata are sealed in the [descriptor](archive.json).
The local-only bundle contains 124 files; ZIP SHA-256
`b0a746b9fb198c1c9fbc95821681da278661a53a2e5369e9749ada7bcd352a12`.
Location: `artifacts/evidence/phone-screen-power-prototype-20261113/evidence.zip`.
Retain locally until explicitly removed; no upload, public captures or remote
retention guarantee. Reviewers can use the descriptor to verify local ZIP/members.

Current guarded trial binds helper JAR SHA-256
`21970ae7107c0d948af4847096ad61c78bfe33400004ae8e820c307b46b6e63f`
and fixture APK SHA-256
`4470c035822f49c26094be01debc92a32828ab7bf4d6582aaf0a8cf861c17d87`.
These final private sources/binaries are included. Earlier helper revisions were
overwritten during iteration; their reported hashes/observations are historical,
not reproducible binaries in this archive. The initial inspect-only native exit
crash has no independent raw log. Initial host startup/CRLF failure metadata and
the manual timeout remain available as failed/unverified attempts.

Commands: private `build.py`, `run_proof.py`, `build_fixture.py`,
`install_fixture.py`, `guard_trial.py --mode on/off/manual`; requirement/coverage/
documentation validation and two Mermaid renders. Current guard-on
`20261113T135811Z` and guard-off `20261113T135830Z` exit 0; manual
`20261113T135912Z` exits 1 waiting for OFF. No performance distribution or physical
panel appearance confirmation is claimed. The host/logcat bridge is an experiment,
not the proposed production dispatch path. Full product host/device regression,
rendered product UI acceptance and final publication reviews are out of scope
while there is no product implementation or release candidate.
