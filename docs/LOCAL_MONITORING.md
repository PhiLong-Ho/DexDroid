# Local run monitoring

Device runs and commands explicitly wrapped with this supervisor print an exact
attach command. Direct Gradle/tooling runs need the wrapper below. In another terminal:

```sh
python tools/run_monitor.py watch "<run-directory>/monitor"
python tools/run_monitor.py status "<run-directory>/monitor"
```

The viewer only reads files. Ctrl+C detaches it; work continues. Missing, malformed
or stale status remains unknown. Completed runs can be inspected later. The view
prints state changes and a periodic summary, without starting/cancelling/retrying work.

Explicitly supervise an arbitrary command in a new directory:

```sh
python tools/run_monitor.py run --output .local/my-run --timeout 120 --idle-warning 30 -- python -u -c "print('hello')"
```

Use unbuffered producer output where supported. Bytes a child has not flushed
cannot be observed. Ctrl+C in this runner cancels owned work. Windows Job Objects
or POSIX process groups clean up ordinary descendants, including on normal exit;
Windows leaders start suspended, join their cleanup Job Object, and only then
resume. Progress extraction cannot hold the deadline-control lock. Background
services and deliberately escaped descendants are unsupported. This is
not a security sandbox. No cloud service, model call or always-on daemon is used.

## Status and limits

Heartbeat proves supervisor liveness, output is activity, and extracted assertion
labels are progress hints rather than test-case counts. Unknown totals stay unknown.
The owning runner's validated results decide acceptance; a completed command session
does not certify a full quality/release run. No agent/provider waiting time is inferred.

Defaults are a 900-second deadline, 60-second quiet-output warning, two-second
heartbeat, 30-second stale-supervisor threshold, 64 MiB output per command plus at
most an in-flight chunk per stream, and 20,000 events per session. Silence warns
without stopping work. Deadline/cancellation, capture failure or limits cannot pass.
Extractor failure preserves continued raw capture and fails the command. Both
streams remain available after timeout. Process/stream cleanup has bounded waits.

Each command writes live byte-preserving stdout.bin, stderr.bin and a combined log.
Bounded events.jsonl and atomic status.json are separate from raw capture. Event
metadata omits command arguments/environment/raw prompts; actual raw output may
contain sensitive data. Limits may leave incomplete capture, explicitly failed.
Direct runs return 124 for timeout, 130 for cancellation, 74 for capture/limit faults,
127 for launch failure, or the child's exit code. Viewer status returns 0 for completed,
1 for non-success/pending, and 2 for unknown; viewer interruption is detach-only.

## Adapters and evidence

Template project.json quality/build commands accept positive finite numeric
timeout_seconds and idle_warning_seconds. Existing command.log files are retained
alongside monitor/. Quality context binds source commit/content digest; existing
acceptance and release validators remain authoritative. A final monitor fault makes
the last otherwise-successful outcome exit 74 with child_exit_code 0 retained.
Late monitor faults cannot certify host/build success.

DexDroid instrumentation uses the same supervisor and retains --timeout plus strict
Android result checks. Its live logs use `supervisor/`, separate from the native
`monitor/` suite results. Preflight/restoration phases and cleanup outcome are visible;
interrupt cannot become success. Legacy Android suites can buffer assertion output
until the final result; progress remains unknown until the producer emits it. No
scenario selector or new Android progress producer is added by this change.

Use [coding practice](CODING_PRACTICE.md) and the
[feature contract](specs/local-monitoring.md). Keep raw runs ignored under .local/
or versioned testresults runs; archive failures/bytes under [EVIDENCE](EVIDENCE.md).
No automatic deletion, remote upload or telemetry collection is installed. Keep
the reusable supervisor/tests aligned with the template; adapters stay project-specific.
