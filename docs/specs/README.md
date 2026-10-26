# Feature specifications

Retrospective contracts make existing behavior reviewable before the next change.
These documents use [the Spec Kit structure](../templates/spec.md): prioritized
stories, Given/When/Then scenarios, requirements, entities, outcomes and assumptions.

[SPEC.md](../../SPEC.md) remains the authoritative full baseline. Existing FR/NFR/DFR
and stable DX IDs are preserved; feature-local IDs in the older split/prototype
specs explicitly map back to it. Update the owning feature, baseline and requirement
map together when behavior changes. This backfill records contracts, not new
implementation, current verification, or approval of old screenshot baselines.

## Feature ownership

Every baseline requirement has one owner in this index.

| Feature contract | Baseline requirements | Stable requirement IDs |
| --- | --- | --- |
| [Saved workspace shortcut](saved-launch-shortcut.md) | FR-038 | DX-SHORTCUT-001 |
| [Session media output](session-audio.md) | FR-036 | DX-AUDIO-001 |
| [Local development monitoring](local-monitoring.md) | NFR-011 | DX-OBSERVE-001 |
| [Coding practice](../CODING_PRACTICE.md) | NFR-010 | DX-PRACTICE-001 |
| [Desktop session and Android compatibility](desktop-session.md) | FR-016, FR-017, FR-023 | DX-LIFE-001, DX-LIFE-002, DX-COMPAT-001 |
| [App launching, catalog and task handoff](app-launcher.md) | FR-005, FR-018, FR-025, FR-026 | DX-IDLE-001, DX-LAUNCH-001, DX-HANDOFF-001, DX-CATALOG-001 |
| [Phone input, monitor navigation and search keyboard](input-and-navigation.md) | FR-006, FR-007, FR-008, FR-009, FR-010, FR-011, FR-029, DFR-001 | DX-KEYBOARD-001, DX-NAV-001, DX-NAV-002, DX-NAV-003, DX-INPUT-001, DX-INPUT-002, DX-FOCUS-001, DX-OPTIONAL-001 |
| [Display configuration and per-app preferences](display-and-app-settings.md) | FR-012, FR-013, FR-014, FR-015 | DX-SCROLL-001, DX-PROFILE-001, DX-DISPLAY-001, DX-DISPLAY-002 |
| [Shared controller UI, icon and installed version](controller-ui.md) | FR-001, FR-002, FR-003, FR-004, FR-024 | DX-UI-001, DX-PHONE-001, DX-TEXT-001, DX-ICON-001, DX-VERSION-001 |
| [Shizuku setup and offline user guide](setup-and-guide.md) | FR-034, FR-035 | DX-SETUP-001, DX-GUIDE-001 |
| [Named monitor profiles and remembered bindings](monitor-profiles.md) | FR-020, FR-021, FR-022 | DX-MONITOR-001, DX-MONITOR-002, DX-MONITOR-003 |
| [Protected-screen detection and troubleshooting](protected-content.md) | FR-027, FR-028 | DX-SECURE-001, DX-PROTECTED-001 |
| [Diagnostics and current-state visibility](diagnostics.md) | FR-019 | DX-DIAG-001 |
| [Verification, documentation and release maintenance](verification-and-release.md) | NFR-001, NFR-002, NFR-003, NFR-004, NFR-005, NFR-006, NFR-007, NFR-008, NFR-009, NFR-012 | DX-PERF-001, DX-VISUAL-001, DX-MAINT-001, DX-DOC-001, DX-RELEASE-001, DX-COVERAGE-001, DX-REVIEW-001, DX-REPORT-001, DX-BRANCH-001, DX-ARCH-001 |
| [Debug-only feasibility prototype](two-pane-prototype.md) | FR-030 | DX-SPLIT-001 |
| [Managed split view](split-view.md) | FR-031, FR-032, FR-033 | DX-WINDOWS-001, DX-WINDOWS-002, DX-WINDOWS-003 |

## Architecture navigation

[Kotlin migration](kotlin-migration.md) is the active maintenance contract for
NFR-014 / DX-MIGRATION-001; its phased plan preserves all feature contracts above.

The [complete source-linked atlas](../architecture/README.md#feature-diagram-atlas)
provides overview/sequence pairs for every row above, including Coding Practice
and the debug-only prototype. Every task records impact; possible or uncertain
architecture changes need [expert preflight](../WORKFLOW.md#architecture-before-implementation).

## Use this with the workflow

1. Find the owning contract above and read its linked checks/gaps.
2. Update its story/scenarios and the baseline before implementation. Preserve stable
   IDs; extend requirements.json only for new or changed acceptance obligations.
3. For interaction changes, obtain the [UI expert proposal and material user
   decisions](../WORKFLOW.md#before-implementation) before dependent implementation.
   Clarify unresolved requirements; keep architecture and feature-flow diagrams
   reconciled. Implement, then update behavior-focused acceptance/E2E checks. Any test-source edit
   requires reconciled concrete checks and regenerated coverage files.
4. Run host checks and applicable device suites using the [testing runbook](../TESTING.md).
   Record exact configurations and exclusions, inspect renders and measure performance
   where affected. A spec or a test mapping is not evidence that a scenario passed.
5. Follow [the review gate](../REVIEWS.md) for the final candidate before committing/
   publishing under project policy. Existing approvals do not cover changed content.

The [requirements inventory](../requirements.json) and [generated coverage](../coverage.md)
own detailed test mappings and gaps. [Design](../DESIGN.md) owns visual tokens;
[architecture](../ARCHITECTURE.md) owns implementation; [release instructions](../RELEASING.md)
own publication commands. [Developer onboarding](../ONBOARDING.md) gives the entry
route; [evidence storage](../EVIDENCE.md) keeps new raw runs out of Git.
[Versioned evidence](../../testresults/README.md) records actual runs.

- [Turn off debugging](debugging-off.md): explicit one-way session shutdown and per-flag results (FR-037 / DX-DEBUGGING-001).
