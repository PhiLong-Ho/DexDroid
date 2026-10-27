# Device capabilities: Android11 scoped acceptance

Development0.16.1-dev/code44 on Black Shark4/JoyUI/API30 with a real1920×1200
monitor. Dirty feature branch based on28a58fe; not a published/final-reviewed tree.
No broad OS/OEM regression or universal hardware certification claimed.

- Final native run `20261027T164815.004628Z`:550 assertions passed in15.022s.
- Actual phone/monitor Settings entry, six passive items, Refresh without setup/
  layout/audio changes, diagnostic Copy and Up/Close navigation, four rotations,
  and1920×1080@240 Fit with rollback/cleanup.
- 267 JVM tests pass; tooling210 pass and1 Windows privilege skip. Required
  requirement/coverage/docs/privacy/Ruff checks pass. Coverage remains partial.
- All12 final captures inspected and approved by `/root/capabilities_render_review`
  at2026-10-27T16:49:34Z; the prior scrollbar finding is corrected, including Fit
  bottom. No new pixel golden or unpictured-state approval.
- Seven source-bound architecture/spec Mermaid blocks rendered; affected diagrams
  inspected. Source architecture follow-up approves scoped invariants separately.

Installed app SHA256:
`2a04f6a698b3f4f151dfde460517536446b79e5d8faa1b2a6bcdf7d374cdfda0`.
Test APK SHA256:
`fe824a23f2590a78462955750409a00b5f99236f660a7b6e54d7ed7bc5985d25`.
161 app/build source hashes and signed/unsigned payload equivalence verified.
Separate test APK removed; controller/report and desktop remain usable.

First run163906 failed monitor Refresh reachability; test scrolling/frame
synchronization corrected while preserving assertions. Prior run164356 passed546
assertions before the shared scrollbar inset and additional Fit-bottom capture.
Those raw attempts remain in the bundle, not recast as the final pass.

Current host tests cover unknown/denied states, optional audio independence,
owner/readiness loss and recovery, stale outcomes, deadline/close/destruction,
bounded refresh and sanitized immutable exports. Physical disconnect/helper death
during collection, unpictured error/progress/disconnected states, large fonts,
hardware keyboard/screen reader, real input observation transitions, otherOS/OEM,
performance and acoustics remain unverified. Guided testing is deferred.

Completed private collection:
`testresults/0.16.1-dev/runs/20261027T165123.250423Z-capabilities/`.
[Archive descriptor](archive.json):133 files, verified member/ZIP hashes,
SHA256 `3d965c3b6a3a18c5b4c158003e9768d5d27098e2af0328e4380f28dceeabf563`.
Availability: **private local-only**, retained until owner deletion at
`artifacts/evidence/device-capabilities-android11-private/evidence.zip`.
No upload authorized; never attach this personal-device bundle to GitHub.

[Change record](../../../docs/changes/device-capabilities.md) ·
[Latest private UI references](../../../docs/ui-references/device-capabilities.json)

```sh
python tools/evidence.py verify --archive artifacts/evidence/device-capabilities-android11-private/evidence.zip --descriptor testresults/0.16.1-dev/device-capabilities/archive.json
```
