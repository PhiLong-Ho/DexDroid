# Change: local command monitoring

## Contract before implementation

User authorized a free local streaming runner and separate status view. Baseline
NFR-012 / DX-OBSERVE-001 and its feature contract precede implementation; the
template counterpart is NFR-009 / WF-MONITOR-001. Preserve required checks and
independent reviews. No app behavior, cloud/model calls, telemetry collector,
automatic retries or per-scenario Android producer/selector is introduced.

## Experience preflight

Read-only advisor `/root/monitor_experience_preflight` inspected the existing
buffered runner and recommended an attach-only terminal view. The user authorized
this local setup with "proceed". Existing run commands print the directory; another
terminal shows liveness/activity/progress, deadlines and unknown/stale state. Viewer
Ctrl+C detaches; runner cancellation preserves failure and attempts restoration.
No unresolved material UI choice or Android product screenshot change applies.

## Implementation and acceptance

Reusable supervisor/tests match agent-dev-template. Raw stdout/stderr are available
before child exit; extracted progress and atomic status are separate. Time/output/
event bounds and Windows Job Objects/POSIX groups bound ordinary owned processes.
The Android adapter preserves strict final-result checks, actual APK/runner hashes,
timeout stderr, cancellation, suite metadata and controller restoration outcomes.
Even monitoring write failure cannot skip restoration or certify a pass.

Host fixtures test independent observation, live raw bytes, quiet warnings, deadline/
cancel, descendant cleanup, missing/nonzero commands, capture/extraction/limit faults,
stale/malformed/bounded status and viewer detach. Adapter fixtures cover timeout,
cancel, restoration and monitor failure. Existing host tests/build/lint remain
required. Prior coding-practice guidance is included in this candidate as requested.

Architecture/feature diagrams are maintained and rendered separately from product
UI approval. Current raw runs and compact evidence index use
`testresults/unreleased/local-monitoring/`; raw logs/render evidence remain ignored
and available locally. Requirement coverage is refreshed with test-source changes.

## Limits

Legacy Android suites may buffer assertions until completion; unknown progress
remains unknown. One normal icon suite and controller restoration passed on Android 11 SHARK with
matching installed APK hashes. Host ADB fixtures do not certify real JoyUI/Samsung timeout,
interrupt/restoration or physical-monitor behavior. No new phone installation is
implied. Windows and POSIX execution are reported separately. No benchmarked speedup,
human product visual approval, hosted CI/release or model-delay attribution is claimed.
Raw output can be sensitive; nothing is uploaded or automatically deleted.

Final corrections cover suspended Windows launch before job assignment, deadline
enforcement independent from slow extraction, accurate interrupted-restoration
state, and tracked compact evidence descriptors. Regression cases exercise these
observable failures; earlier candidate evidence remains historical.

Finalization regressions also cover asynchronous post-command monitor faults and
the final-event cap. Authoritative runner/host/build outcomes refuse success while
preserving child exit and independent controller-restoration evidence.

Observer CLI regressions distinguish missing/invalid/stale state (exit 2) from
observed non-success. Template adapters retain previously valid command names
through bounded internal labels without changing exact outcome/log names.
