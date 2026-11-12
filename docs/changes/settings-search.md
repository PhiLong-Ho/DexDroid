# Change: Settings search and Developer Mode label

## Requested contract

The user requests a search bar for related Settings and the exact visible name
"Turn off Developer Mode" instead of "Turn off debugging". Phone and monitor
must share matching semantics and monochrome design. Existing menu-readability
work on `feature/menu-readability` is the authorized base; unrelated local edits
remain untouched. No publication or version bump is requested.

The rename is explicitly authorized. It changes labels/page titles/guide wording,
not the existing confirmed shutdown, three settings writes, per-flag readback,
interruption/recovery or ordinary Stop behavior (FR-037 / DX-DEBUGGING-001).

## Preflight and settled design

Fresh read-only UI and architecture CLI workers reviewed search scope,
placement, query lifetime and keyboard ownership. The collaboration tool hit its
thread limit, so independent `codex exec` workers are used instead. Their initial
Windows read-only shell was denied; no review outcome is inferred from that error.
Search does not activate actions while typing or acquire shell authority.

UI expert `01a1d034-2aaa-78c1-a733-c5ca51119078` recommends a fixed search field
below version, filtering existing rows using titles/section names/static aliases,
with native phone and local monitor keyboards, retained child-return query and
reset on Close. Architecture expert `01a1d034-2aa8-7e51-b2a0-18666779dedd` returns
**approve-approach** for that bounded design: explicit stable metadata, independent
surface state, owner-aware editor teardown, in-place filtering and eligibility
composition; no service/IPC/permission/persistence expansion. Neither is final
code, release, rendered or performance approval.

Review `R-1794494725-0-64974265253888` returned **Existing pages**. The question
explicitly includes fixed placement below version, related-keyword matching,
Clear/no-results, retain on Up and clear on Close. The alternative direct nested
index was not chosen. This settles the material scope/navigation decision; no
answer is inferred from silence. FR-042 / DX-SEARCH-001 records it.

Implementation keeps a pure shared matcher and explicit row bindings. Each surface
owns its native editor, query, scroll and keyboard lifetime. Existing service
commands, conditional native-keyboard Retry and live control updaters retain their
owners. Filter containers compose with conditional child visibility. Monitor
Apps and Settings queries must stay separate across rebuilds; old editor/watchers
cannot affect a replacement menu. Android 11+ native APIs and local keys are reused.

## Implementation and verification scope

`SettingsSearch` owns static IDs/titles/sections/aliases and deterministic all-word
matching. `SettingsSearchPanel` supplies shared fixed version/search/feedback,
Clear and explicit visibility bindings. Existing rows stay live; native keyboard
and conditional Retry share one filtered group. Query length is bounded to 200
characters. MainActivity retains visited Settings query with its MenuEntry;
MonitorShell keeps it apart from launcherQuery and checks the editor's owning
page. Closing/replacing destroys the watcher and makes retired-editor work inert.
The exact Developer Mode rename reaches Settings, page titles, guide and current
documentation; underlying private names/permissions/shutdown operations are unchanged.

Acceptance adds three pure matcher tests and real phone/monitor flow checks inside
the existing suite entry points. It checks related terms/no-results/count wording,
child return, Clear/Close reset, retired editors, native phone IME, local monitor
key contacts/Back and the renamed warning page. The existing Apps keyboard suite
is retained to check the generalized owner guard. All four monitor rotations and
the existing separate Fit preview remain in the monitor suite. No full suite
matrix or other OS/OEM testing is claimed. The prior workspace-input failure is
outside this change and is not recast as fixed or retested.

Rendered inspection corrected singular result wording. An additional monitor
capture showed a frame before Clear's layout settled, despite correct native
visibility state. The screenshot helper now waits for submitted animation frames;
that is a capture-only test change. Existing host outcomes and keyboard/shutdown
passes are reused only with disclosed source/build scope; final phone/monitor
acceptance follows the wording/capture corrections. No asynchronous search worker,
performance benchmark or human-approved screenshot golden is claimed.

## Completed delivery

The final product is installed on the physical Xiaomi running Android 16. Phone
UI and four-rotation monitor/Fit acceptance pass; original Apps keyboard and
controlled shutdown passes are scoped to unchanged behavior before count/capture
polish. Host: 290 JVM plus 203 tooling passes, 8 Windows skips. See the
[versioned summary](../../testresults/0.18.0-settings-search/summary.md) for exact
source/APK/run/timing bindings, reuse, gaps and verified private archive.
[Current references](../ui-references/settings-search.json) retain 11 capture
hashes and the normalized monitor sheet; prior readability references point to
the superseding Settings layout.

Test helper removed, controller reopened. Spec, README, in-app guide, coverage,
design and component/sequence diagrams reconciled. Onboarding setup/workflow are
unchanged; the architecture atlas identifies new owners. Unrelated policy and
promotion edits remain untouched. No version, publication or final commit/push
approval is claimed.
