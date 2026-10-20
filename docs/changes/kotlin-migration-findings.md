# Kotlin migration: notable findings

The Android source conversion and Gradle Kotlin DSL conversion are complete.
The final focused Xiaomi14 Android15 input and keyboard checks pass. Publication
requires the separate architecture and final candidate review gates. The user ended further Android12-14 campaigns to limit maintenance cost;
other OS versions will use community feedback. Earlier failures stay recorded.

The latest complete API35 emulator campaign passes all eleven suites, both
focused navigation suites and disconnected controller. The substituted physical
Xiaomi passes all eleven suites, settled single navigation, three normal split
navigation sessions and one warm single-to-split navigation sequence. An earlier
rejected-DOWN failure remains unexplained; subsequent passes establish
nonreproduction in those configurations, not a proven fix. Original Android11
hardware acceptance remains incomplete because that phone became unavailable.

## What changed

All 82 baseline maintained Android Java files are converted. The project now has
89 Kotlin Android source files, including bounded diagnostic/launch owners and tests, and
three Kotlin DSL build scripts. Generated AIDL Java remains generated; XML,
AIDL, Python, shell and assets retain their formats. This migration preserves
native Views and the existing architecture; it adds no Compose/coroutine redesign.

## Findings

1. **JVM contracts need explicit verification.** Android/Shizuku entry points,
   static methods/fields, constructors, Binder signatures and Class monitor locks
   must survive translation. Compilation alone does not establish equivalence;
   phase checks inspected ABI shapes and relevant bytecode as well as behavior.
2. **Java and Kotlin null behavior can differ.** Four native-test sites needed
   explicit `String.valueOf(Object)` to preserve Java's null text. A direct
   converted call crashed on a null content description. The assertions stayed.
3. **Standalone fixtures need their own runtime.** Instrumentation could access
   the app's Kotlin runtime while separate-UID fixture processes could not.
   Test-only packaging now includes the pinned runtime with a distinct artifact
   basename to avoid transformed-artifact aliasing. Production fixture exclusion
   and separate-process native launches are checked independently.
4. **Readiness must describe usable state.** A Shizuku binder ping can precede
   service initialization. Compound profile state needs a coherent main-thread
   observation, and positive native input needs settled task/display/geometry
   ownership. Harness repairs preserve original deadlines and assertions.
5. **Cancellation needs platform evidence.** The API 30 emulator delivers a
   canceled key release even when DexDroid suppresses its own release. Removing
   that guard could duplicate events. The earlier physical split failure remains
   unresolved; no Kotlin-specific cause has been established. New diagnostics
   preserve fixture identity, fresh event times and the original failure outcome.
6. **Native success and trace completeness are separate.** API 30's split test
   passes while later focus callbacks exhaust its bounded lifecycle logger.
   That whole trace remains inconclusive. API 36 lacks shell decision records;
   neither emulator trace authenticates every producer or proves losslessness.
7. **Lint baseline migration needs narrow review.** Kotlin analysis exposes
   existing calls whose Java baseline locations no longer match. Source-linked
   relocations and evidenced inherited records were reviewed individually;
   no blanket regeneration or detector suppression was used.
8. **Emulators do not establish universal hardware compatibility.** API 37's
   focused input passes, but retained experimental teardown failures remain.
   Real monitor, OEM input, peripheral and lifecycle coverage stays explicit.
9. **Capability queries must match the actual caller.** A retained Android12
   native failure rejects trusted display creation from shell UID2000. Later
   package-only permission queries also return denied on images with earlier
   native desktop passes; those queries cannot establish actual caller authority.
   The final matrix measures the shell process PID/UID through ActivityManager
   separately. No root/grant bypass or untrusted-display change is introduced.
10. **Keyboard focusability and pointer readiness differ.** Android13's consumed
    report identifies lone `NOT_FOCUSABLE` as the strict touch prerequisite's
    rejection, with matching task/UID/frame/region. Its overlap and final task
    checks were short-circuited. Platform pointer dispatch admits touchable windows
    independently of this keyboard flag. A caller-only API33 test-model correction
    passes the corrected native input suite. No keyboard pass follows from that
    prerequisite alone; the separate API33 keyboard suite also passes.
11. **UI assertions need UI-thread ownership too.** The inherited phone page/Back
    predicates read views from instrumentation. The same predicates now run on
    main against the captured Activity, with bounded content-free owner evidence.
    UI suites pass on API30/35/36. This does not prove that the unsafe observation
    caused the original API35 failure, and does not change product navigation.
12. **Task placement does not prove rendered output.** Android14 passes seven
    suites after normal fullscreen onboarding, then the handoff test captures
    departed parent pixels while task/window metadata describes the remaining
    app. A reconstructed matching Java debug pair from the clean published source
    reproduces the same Android14 assertion and departed-parent pixels. This
    demonstrates that observable exists before migration. Paired native probes
    subsequently isolate a transition-finish Surface-parent race. A narrow launch
    path waits for its actual native completion and revalidates placement; the
    original child and fresh remaining-app input pass on Android14/15. Arbitrary
    phone movement before completion remains unresolved. These controls use newly
    built debug artifacts, not the original published release binaries.
13. **Physical-host routing is a separate prerequisite.** The latest API36 input
    run passes virtual-desktop readiness and targeted input, then Android drops
    the physical-host DOWN because display2 has no touchable window. Cleanup
    closes the host afterward. Failure-time host state is missing, so the reason
    for that window's absence is unresolved; no API33 flag generalization follows.
14. **Native metadata layout changes need conservative parsing.** Android14
    joins the visibility field to its seamless-rotation diagnostic. The inherited
    parser misses a secure pane despite native input and a visible surfaced
    window. A narrow parser correction accepts complete standalone/known joined
    fields, rejects ambiguous fields and adds four host cases; native verification
    remains separately recorded.
15. **Guard the actual UI mutation after asynchronous work.** A launch can
    return after Stop. Guarding an outer runnable is insufficient if reporting
    posts another unguarded mutation. Single-view success/failure now checks
    captured generation/display/bridge/mode at the actual main-thread update;
    three host ownership cases cover replacement, Stop/restart and split. Actual
    native callback/input/Stop contention remains unverified.

16. **Signal size and logical viewport are different contracts.** Xiaomi signals
    1920x1200 while its input/render host is1920x1242. Hardcoded signal-size test
    points cause21px alignment errors or rejected edge swipes. Shared measured
    fixture snapshots repair the tests, preserving independent expectations and
    the original tolerance. Old/new emulator comparisons and full Xiaomi suites
    pass with an unchanged product APK.
17. **Test prerequisites need platform-specific parsers.** Focused navigation used
    an Android11-only input-window predicate on Android15 and failed before a tap.
    API30 retains that predicate; API31+ uses existing strict modern readiness with
    immutable parent/child identity. Missing old consumed reports limit causality
    claims. The corrected complete emulator navigation scenario passes; physical
    settled-focus verification passes separately from the successful full campaign.

18. **Resumed does not mean focused.** Xiaomi's immediate Back during a child
    transition delivered DOWN to the parent, canceled its UP, then delivered only
    UP to the child. A bounded test prerequisite using existing fixture focus
    reports passes settled-focus navigation. API30 key behavior stays unchanged.
    This does not repair or verify immediate-transition Back; API31-33 application
    of the new prerequisite remains unverified.
19. **Intermittent input failure can hide behind a full pass.** Xiaomi's full split
    campaign passes, then focused second-pane navigation rejects a relayed DOWN
    and stops the desktop. The later physical UP and cancellation also fail after
    host teardown. Retain those secondary failures separately; the exact consumed
    shell decision is not captured by the existing emulator-only debug observer.

20. **Diagnostic wording can collide with machine verdicts.** The first final
    physical input run completes273assertions and returns native completion, but
    the strict host parser rejects a success description containing the reserved
    word "failure." The description is corrected without changing predicates,
    assertions or parser. The original runner outcome remains failed; a fresh
    test artifact requires focused verification.
21. **A visible app row is not a verified native action.** The final Chrome
    check sees an enabled visible row with no clickable ancestor. It sends no
    Chrome/Stop action and remains incomplete. Broadening the selector to tap
    that coordinate would hide an unverified action contract, so this release
    keeps the conservative selector and reports the gap separately.

## Verification scope

| Scope | Actual outcome |
| --- | --- |
| Current host checks | Nine gates pass;200 JVM outcomes valid through Gradle incremental checks;210 tooling passes and one Windows symlink skip |
| Full API35 emulator | Eleven suites, focused legacy/split navigation and disconnected controller pass with the original test APK |
| Full Xiaomi14 Android15 | Eleven suites and collection pass; original product/test hashes retained per run |
| Later Xiaomi navigation | Settled single navigation passes; three fresh normal split sessions and one warm legacy-to-split sequence pass |
| Retained Xiaomi failure | Earlier pane2 DOWN rejected, followed by teardown; consumed decision missing, cause unresolved |
| Latest readiness fixes | Test-only observation fixes verified on Xiaomi:input273assertions, keyboard2405assertions; native Chrome selection remains incomplete |
| Android12/API31 | Actual shell-caller trusted-display capability denied; icon/controller pass, desktop-dependent checks blocked |
| Android13/API33 | Latest full attempt fails input readiness; remaining checks unrun; retrospective cache provenance limited |
| Android14/API34 | Latest full attempt passes input/UI/monitor then fails keyboard row readiness; one native Chrome Stop cycle passes before the next launch selector fails |
| Android11,16,17 | Earlier partial results/failures retained; no current universal compatibility certification |
| Android12L/API32 | Unrun |

Historical runs are not a uniform final-candidate matrix: APK generations,
configurations and test observations differ. Reuse is source-bound and attributed
to each original run. Final focused checks exercise changed observations on the
requested physical device; they do not relabel older failures. No full visual
catalog, hardware keyboard/mouse matrix, rendering latency or recovery-data
identity claim follows from these tests.

The [migration record](kotlin-migration.md) and
[phase evidence index](../../testresults/0.13.0/kotlin-migration/README.md) retain
exact inputs, failed attempts, review scopes and private archive availability.
The final focused input run `20261020T160518.012469Z` passes in31.993s and
keyboard `20261020T160845.434782Z` passes in55.654s. Independent keyboard
collection and final guarded cleanup pass. The interrupted outer run retains its
raw metadata and separate terminal observation. The
[release architecture record](kotlin-migration-release-architecture.md) and final
candidate approvals are separate publication gates.
