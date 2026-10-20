# Feature Specification: Kotlin project migration

**Feature Branch**: `chore/kotlin-migration`

**Created**: 2026-10-18

**Status**: Conversion complete; targeted readiness fixes and release gates pending

**Input**: After stable Java publication, migrate the whole Android project in
phases; regression-test each phase on emulators, perform one full Android 11
hardware campaign at the end, and publish one migration release.

## User Scenarios & Testing

### User Story 1 - Keep the existing desktop behavior (Priority: P1)

Users can update in place and continue using the same phone/monitor controls,
apps, rotation, split view, input and saved monitor/app preferences.

**Why this priority**: A language change must preserve the working desktop.

**Independent Test**: Run the existing acceptance scenarios against each affected
phase, compare native renders, then complete final emulator and Android 11 checks.

**Acceptance Scenarios**:

1. **Given** the stable Java build and saved settings, **When** the Kotlin build
   replaces it in place, **Then** signing/package identity and settings survive.
2. **Given** either UI surface, **When** existing controls and native input are
   used in four monitor rotations and Fit, **Then** the existing contract holds.
3. **Given** held input, resize, task handoff, Stop or failure, **When** ownership
   changes, **Then** cancellation, stale-work rejection and cleanup remain intact.

### User Story 2 - Maintain a complete Kotlin project (Priority: P1)

Maintainers can build, format, test, inspect coverage and release without retaining
handwritten Android Java or losing tests/development-code exclusion during renames.

**Independent Test**: Audit maintained sources and run host/tooling/release guards.

**Acceptance Scenarios**:

1. **Given** mixed Java/Kotlin during a phase, **When** coverage is generated,
   **Then** every concrete test entry remains mapped with canonical fingerprints.
2. **Given** Kotlin debug/test declarations, **When** any definition or facade
   leaks into production, **Then** APK/AAB validation fails.
3. **Given** all phases completed, **When** source/build inventory is inspected,
   **Then** maintained Android sources and Gradle scripts use Kotlin/Kotlin DSL.

### Edge Cases

- Reflective names, static fields/functions, inherited services and constructors
  must retain the JVM contracts used by Android, Shizuku, shell tools and tests.
- Generated AIDL Java is not a maintained source conversion target.
- Android 17's accepted experimental limitation remains an explicit baseline gap.
- A failed phase blocks its dependent phase, except for the narrowly bounded
  inherited API 30 emulator failures recorded in the migration plan: profile
  first-Controls MOVE timeout after phone save/rename/dismiss on retained AOSP
  inputs, separately evidenced preview/Revert profile MOVE timeouts, and the
  Google-image split controller-return missing window.
  Its unknown cause, current/control hashes and failed acceptance remain explicit;
  it never waives new failures or required input/keyboard/child/Stop checks.

## Experience review

No layout, navigation or setup redesign is intended. Existing shared native UI
and approved design decisions remain authoritative; actual render parity still
requires bounded visual acceptance. UI preflight is N/A for language-only changes.

## Architecture links

The [migration plan](../changes/kotlin-migration.md) contains phase overview and
sequence diagrams and architecture preflight. The [system/component map](../ARCHITECTURE.md)
and [feature atlas](../architecture/README.md) retain runtime ownership.

## Requirements

### Functional Requirements

- **FR-001**: Preserve all existing product behavior and update compatibility.
- **FR-002**: Complete Kotlin source/tooling inventory without silent omissions.
- **FR-003**: Gate each phase on applicable regression. The user substituted
  Xiaomi14 Android15 for unavailable Android11 hardware and on2026-10-20 selected
  completed API35/Xiaomi evidence plus focused readiness checks on the user-selected Xiaomi14 Android15 for release.
  Preserve original Android11 gaps. Map to NFR-014/DX-MIGRATION-001.
- **FR-016**: Preserve the executed Android12/API31,13/API33,14/API34 and15/API35
  campaigns with exact original source/APK/runner/image/boot bindings. The user's
  2026-10-20 decision stops further Android12-14 testing for0.13.0 and invites
  community verification. Their failures, capability blocks and unrun tails stay
  explicit. Use completed API35 and Xiaomi14 results, then verify the targeted
  readiness fixes on the user-selected Xiaomi14 Android15 only. Reused unaffected outcomes retain original
  artifacts and dependency exclusions; they are not new-test-APK executions.
  Native, capture, visual, performance and physical coverage remain distinct.
- **FR-004**: Native test setup waits at most five seconds for Shizuku's initialized
  service notification before checking permission. Remove the readiness listener
  on success, timeout or interruption; an absent or unauthorized service remains
  an explicit setup failure. A live low-level binder alone is insufficient.
- **FR-005**: Profile acceptance reads monitor identity, association and compound
  session conditions in one main-thread observation, requiring a present monitor
  even when expecting no association. Test profile mutations use that same owner.
- **FR-006**: Child-launch acceptance observes only its expected parent/reader/exit
  report family and fixture UID/action. Reports from the navigation fixture cannot
  replace its observation. Keep expected-report placement, dimensions and input
  checks unchanged; missing reports remain bounded failures. Exercise unrelated
  same-UID reports through the registered receiver before real native navigation.
- **FR-007**: After successful legacy navigation assertions, use native Up and root
  Back to exit the known fixture task and verify its absence before Stop. Retain
  unconditional Stop/input/receiver/binding cleanup and original failure evidence;
  never send blind navigation when a failed scenario leaves ownership unknown.
- **FR-008**: Split-launcher geometry assertions admit a visible, completed
  controller layout and successful nonempty baseline rectangle within the existing
  bound. Require one immediate successful nonempty rectangle after dismissal and
  unchanged exact equality. Never wait for equality or reset insets/scroll/status.
- **FR-009**: Release, native Stop and device-evidence tools share one strict
  literal version reader. Require one app script and an independent
  android/defaultConfig block, with a fully consumed six-scalar-property contract.
  Reject duplicate, computed, conditional, setter/suffix and unsupported forms,
  unsafe names, nonpositive/leading-zero version codes and malformed structure
  before creating output or touching a device. Explicit safe evidence-version
  overrides remain supported; binary version verification stays independent.
- **FR-010**: Independently launched fixture Activities in the separate test APK
  retain their own UID and load the required Kotlin runtime without relying on
  the instrumented app's classloader. Keep app/test runtime versions aligned,
  verify packaged runtime definitions and real fixture launch, and continue
  rejecting fixture definitions in production APK/AAB output.
- **FR-011**: The debug shell snapshot helper prepares a missing process-main
  Looper only after shell UID/argument validation, rejects incompatible current
  thread state and keeps its existing callback-thread cleanup before XML output.
  No Application initialization, main-loop dispatch or input injection is added.
  A standalone process check verifies installed APK identity and UID 2000, invokes
  once within 25 seconds and requires exit zero plus valid display-0 controller
  XML. Malformed, external, empty, failed or timed-out output fails explicitly;
  private logs/state and partial failure output remain available. Main-queue
  lifetime ends with the CLI process; callback threads still quit/join.

- **FR-012**: Physical production-to-test verification preserves the app's user,
  UID, signing identity and data through an in-place update. Capture and validate
  current preferences after the same-key debuggable replacement and before any
  deliberate app/service/instrumentation launch. Retain process/component evidence
  and reject observed execution before capture; never call this a pre-update
  byte backup. Run the existing checks against exact verified private artifacts
  without relaxing installed/local hash equality. Restore saved contents while
  processes are stopped. A uniquely marked, bounded all-buffer execution interval
  starts before force-stop and covers replay through verified production
  replacement. Require target process absence around replay and after replacement;
  reject target process/service/create/resume events, missing markers or unhealthy
  observations. Byte replay is an intermediate milestone, not restoration success.
  Mark preferences restored only after the whole interval and production identity
  pass, then launch and verify the usable controller. A failed interval after
  production replacement requires state-specific reviewed recovery with the saved
  snapshot retained. Preserve primary errors plus independent collection/recovery
  errors. The compact private operation/input contract is tracked in the reviewed
  candidate; ignored operation bytes must match its hashes before execution.
  Binary preference archives MUST NOT traverse the diagnosed host shell-stdin
  path, which normalizes CRLF. For a failed replay, preserve original records and
  use a separately reviewed state generation. Admit only the exact diagnosed
  stopped/no-owned-display state; never launch malformed preferences to stop it.
  A new active-state generation may instead admit the existing visible controller:
  require display0, user/UID/PID, activity/task/window and native surface visibility,
  plus a unique enabled unobscured Stop action, with identity rechecked after each
  hierarchy dump. A null global phone focus is allowed; do not launch or force
  focus. An optional unique Close menus action requires complete fresh readmission
  before Stop. Require owned displays to disappear after that single Stop before
  the marked force-stop/replay interval. Retain current raw preferences before
  parsing, require only the safe original member set, and restore exclusively from
  the intact validated saved snapshot. Metadata-only source rebinding isolates
  the exact DX-MIGRATION-001 criterion addition, with all130other frozen sources
  and both unsigned APKs unchanged; retain the old generation and failed journals.
  Stage the original archive inside a new shell-owned 0700 directory before
  transfer, verify file type/ownership/0600 and full hashes, then hash and replay
  through device-local pipes. Reject stderr, ambiguous output or member mismatch.
  Original failure/test records remain failed; successful recovery does not admit
  further tests or certify the migration candidate.

- **FR-013**: Positive native-contact acceptance after rotation, Fit or resolution
  changes reuses the existing settled input-window prerequisite with captured
  task/component/UID/display and current dimensions checked before and after
  observation. Reject transition snapshots and unknown occlusion. Poll readiness
  only; retain each contact, original delivery deadline, coordinate/rejection
  assertions and automatic rollback. This does not change product input admission
  or promise delivery during system transitions.
  Preview contacts also require unchanged display/rotation/base size/DPI and an
  unexpired conservative 15-second deadline before and after each contact; fail
  rather than accepting a contact at automatically restored geometry.

- **FR-014**: Diagnose the failed hardware held-key cancellation through debug-only
  synthetic traces, at most128records of512bytes per process, using the actual
  existing focus dump and injection decision without extra relay/focus RPC or routing
  changes. Capture queue ownership/generation and completion without dropping
  accepted cancellation during detach. Fixture reports include per-instance
  task/display/UID/monotonic serial/current focus and32callback history entries;
  allow one initial task metadata capture per instance, cached for callbacks and
  reports. It does not establish later task freshness; existing readiness still does.
  no user text or unrelated window identity is logged. Fresh sequence down-times
  and event-times distinguish the three existing cases. Keep the original15second
  await and count/flags/timestamp/repeat/late-UP/no-duplicate assertions. On failure,
  retain the deadline outcome and independently attempt original late-release
  cleanup, then rethrow the primary failure. Later delivery cannot turn it into pass.
  Truncated/missing trace is inconclusive. Diagnostic performance is not ordinary
  release performance. Emulator API30/36 split precede admitted hardware diagnosis.
  User2026-10-19 instruction hands recovery to the user; no further automatic
  preference replay/recovery is required or claimed. Preserve all failed records
  and backups. Subsequent in-place test/prod updates must still preserve UID,
  signature and app data; no product uninstall or clear is allowed.

- **FR-015**: The user-directed non-restoring physical generation uses a new
  immutable contract, source/APK freeze and journal. Current candidate approval,
  helper/input hashes and source membership precede each action. Fresh architecture
  and two final reviews precede signing/device mutation. Sign into isolated new
  output; independently verify certificate/package/version/payload and ordinary
  fixture/debug-source definition exclusion. Main KeyDiagnostics remains a
  separate BuildConfig.DEBUG gating claim. Admit exact stable38/user0/UID/device/
  monitor identity, protected shell0700 helper and native Stop before in-place
  debug39/test update. Stop/identity/install uncertainty blocks dependent work.
  Match exactly one physical EXTERNAL/HDMI DisplayDeviceInfo using canonical
  uniqueId/name/type/integer address-model identity from an immutable bound
  historical dump. Compare reference, contract, current observation and journal
  before preparation mutations and every suite. Missing/malformed/multiple or
  changed monitors fail; logical IDs/mode IDs/geometry/rotation/fps are excluded.
  No product uninstall, clear, downgrade or preference replay is permitted.
  Optional post-update raw prefs capture is informative, not restoration proof.
  Run one focused split reproduction first; a native failure blocks dependent
  work. A pass admits the still-required full eleven suites, then
  focused/controller/cable and native Chrome
  checks sequentially with exact signed installed/local hashes. Filtered collection
  starts before diagnostic intervals; preserve unknown/lost/truncated scope.
  Private shell-cache tar extraction admits only safe bounded regular expected
  members, exact invocation/coherent clock freshness and explicit stale exclusions.
  Production39 delivery requires actual required outcomes and evidence, exact
  UID/certificate/nondebug/hash, then identity-scoped test-package/helper cleanup.
  Primary and independent collection/cleanup failures remain separate; no automatic
  retry or restoration-success claim is allowed.
  The current operation implements signing/preparation/suites; cable, Chrome and
  conditional production delivery remain separately admitted acceptance tails.

- **FR-017**: Phone page and native-Back readiness acceptance evaluates the existing
  predicates coherently on the main thread against the same captured Activity.
  Preserve assertion labels, polling interval/deadline and three Back cycles;
  never reacquire/relaunch an owner, focus a window or retry input. Retain one
  latest bounded content-free snapshot with phase/cycle/time, captured task identity,
  Activity/lifecycle/display/Dialog/window/root/token identity, symbolic tags,
  focus/layout and IME state. Capture task identity once, with no per-poll IPC.
  Persist the snapshot privately before screenshot/cleanup on failure; attach
  independent write failures without replacing the primary error. Main-thread
  synchronization retains the outer runner timeout; the polling deadline is not
  a hard end-to-end guarantee if the UI thread stalls.
- **FR-018**: Failed native input-readiness acceptance may retain one invocation-owned
  sample of the task/input/window reports it already consumed, with expected
  identity/geometry and faithful rejection stage. Preserve predicates, query
  counts, short-circuit order, deadlines and input. Optional observation remains
  absent for other callers. Begin every sample empty, mark unqueried fields,
  bound each retained report to262144 decoded characters and mark truncation
  inconclusive. Acceptance uses the complete original reports. Flush failure-only
  private evidence before preference restoration/teardown; retain the original
  failure and independent write errors. Separately collect this optional artifact
  after a failed input run; its absence never changes a native outcome.

- **FR-019**: DeviceChecks may explicitly opt into API33 pointer-only readiness.
  For this caller and SDK only, admit exactly `inputConfig=0x0` or lone
  `NOT_FOCUSABLE`: Android's keyboard-focus policy does not itself block a
  touchscreen target. Retain complete task/component/UID/frame/region/parser,
  no-overlap and final-task checks, original deadlines and all native delivery,
  coordinate, cancellation, Fit and rollback assertions. Unknown/combined flags
  stay rejected. Other callers/defaults remain strict; do not generalize this
  mode to keyboard readiness, API31/32 or product input. Preserve original failed
  attempts and execute the corrected native suite before claiming acceptance.

- **FR-020**: Native monitor acceptance uses a shared main-thread logical viewport
  snapshot for mapped gestures, PixelCopy extents/samples and layout bounds.
  A finite contact keeps its original frame/origin/display/configuration; verify
  it before DOWN and retain it through UP/CANCEL. Preserve the independent
  intended-coordinate oracle, less-than-two-pixel tolerance, original deadlines
  and intentional stale/busy scenarios. Inline work already on main. Keep genuine
  output-mode assertions. Existing split fixtures explicitly reject nonzero screen
  origins rather than mixing local points with a screen-space clamp.

- **FR-021**: Focused navigation readiness retains the API30 predicate and selects
  existing strict modern task/window readiness on API31+. Capture one immutable
  parent/child fixture page, task, UID, display and virtual geometry per tap;
  match the correct nested child component and revalidate before DOWN. Keep the
  original five-second polling deadline/40ms interval and native navigation/phone
  visibility assertions. Pointer-only admission is API33-specific. Retain only
  bounded consumed failure reports with independent write errors. Missing old
  reports remain missing. API31+ remote Back additionally waits for the captured
  fixture window focus (five seconds/40ms), then atomically revalidates identity
  and geometry before one key. API30 semantics stay unchanged. This is a test
  prerequisite, not a product dispatch repair. Unchanged executed dependencies may reuse their exact
  source-bound original campaign; do not describe that as a new APK rerun.

- **FR-022**: A private debug-only exact Xiaomi API35 secondary-child diagnostic
  captures one existing rejected-DOWN decision under fixed build/role/fixture UID,
  protected nonce/boot/expiry and exclusive output guards. Skip intentional parent
  samples without consuming; null consumes one inconclusive sample. Preserve the
  emulator's original consumption policy, all input decisions and bounded output.
  Exclude the sink from production and verify the updated debug APK independently.
  Diagnostic file transfer preserves exact bytes, verifies bounded size and stable
  protected metadata/path, and matches remote/local SHA-256 before and after the
  read. A text-mode newline conversion is a failed transfer, not valid evidence.

### Key Entities

Final emulator capability admission distinguishes package metadata from the
actual shell caller's PID/UID check. Package denial alone leaves desktop execution
unverified. A separately bound read-only probe may invoke the framework permission
path with shell UID2000, fixed permission/API/boot and compiler/DEX identities;
errors and cleanup failures remain inconclusive. It grants no permission and
creates no application, display, Looper or input.

- **Phase candidate**: Exact source/build/test/configuration bindings and outcomes.
- **Migration baseline**: Published Java tag and explicitly bounded prior evidence.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Zero maintained Android Java files; all Gradle scripts use Kotlin DSL.
- **SC-002**: Required host/tooling and affected emulator gates pass for each phase,
  with only the reviewed, explicitly outstanding API 30 exceptions and blocked
  scenario tails. Final
  ordinary emulator results and any residual exception receive fresh architecture
  and both final review decisions; full Android 11 hardware acceptance must pass.
- **SC-003**: Final hardware, visual, upgrade and release gates are recorded with
  actual outcomes/gaps; one migration version is published.

## Assumptions

- Baseline v0.12.5 is published; migration targets 0.13.0/code39.
- Python/shell/XML/AIDL/assets retain their formats; no UI redesign or new features.
- One planned hardware campaign does not waive necessary failure fixes/retests.


### Monitor search observation follow-up

Keyboard filtering acceptance captures the current shell/editor/list/adapter on
main before typing, then requires expected query, count and a visible, attached,
measured tagged row in that same list within the existing ten-second bound.
A model count alone is not rendered-row readiness. The original rendered-row,
physical key, underlying-app isolation and selection assertions remain. No input
replay or product behavior change is permitted; retain bounded failure observation
and original failed runs. Architecture approach approved by
`/root/emulator_final_matrix_arch` at2026-10-20T15:12:10Z; the scoped implementation changes only test observations and receives focused
Android15 verification, without resuming the stopped Android12-14 matrix.
