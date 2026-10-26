# Structured acceptance summary

Compact outcomes and source reuse bindings; raw evidence remains private.

```json
{
  "version": "0.16.0",
  "version_code": 43,
  "baseline": "e5670c5be9ea6bb16fb08fe7c2cdb2533edb94c8",
  "device": "Xiaomi14 Android16, real1920x1200 monitor",
  "selected_runs": [
    {
      "suite": "workspace",
      "run": "20261026T171832.601816Z",
      "outcome": "passed",
      "assertions": 522,
      "duration_seconds": 88.555,
      "apk_hashes": {
        "dev.dexdroid": "ad0bbbf09afc7da18a3b00ca1f1942b4bcbfe737785e5aff74984045089189b6",
        "dev.dexdroid.test": "1dd8a2e6168bd212d955110ca1df3d8dd73057e394044cdc96476ca62a2815c1"
      },
      "source_reuse": false
    },
    {
      "suite": "ui",
      "run": "20261026T155650.838442Z",
      "outcome": "passed",
      "assertions": 475,
      "duration_seconds": 32.884,
      "apk_hashes": {
        "dev.dexdroid": "6408ac643e36c5e3ccae5dd2095e9d96a052da248ccd9d3af27ccbf84034f7a7",
        "dev.dexdroid.test": "a10ecb60c510367378a7c777ef9f466b94451010718f1bb4ace48430f9bad62d"
      },
      "source_reuse": true
    },
    {
      "suite": "monitor",
      "run": "20261026T172011.361030Z",
      "outcome": "passed",
      "assertions": 2434,
      "duration_seconds": 104.311,
      "apk_hashes": {
        "dev.dexdroid": "ad0bbbf09afc7da18a3b00ca1f1942b4bcbfe737785e5aff74984045089189b6",
        "dev.dexdroid.test": "1dd8a2e6168bd212d955110ca1df3d8dd73057e394044cdc96476ca62a2815c1"
      },
      "source_reuse": false
    },
    {
      "suite": "split-navigation",
      "run": "20261026T160031.396839Z",
      "outcome": "passed",
      "assertions": 1149,
      "duration_seconds": 87.083,
      "apk_hashes": {
        "dev.dexdroid": "6408ac643e36c5e3ccae5dd2095e9d96a052da248ccd9d3af27ccbf84034f7a7",
        "dev.dexdroid.test": "a10ecb60c510367378a7c777ef9f466b94451010718f1bb4ace48430f9bad62d"
      },
      "source_reuse": true
    },
    {
      "suite": "audio",
      "run": "20261026T160337.442954Z",
      "outcome": "passed",
      "assertions": 186,
      "duration_seconds": 19.168,
      "apk_hashes": {
        "dev.dexdroid": "6408ac643e36c5e3ccae5dd2095e9d96a052da248ccd9d3af27ccbf84034f7a7",
        "dev.dexdroid.test": "0c20185c23750be47acc34ea5e8f39536179d290b2222e1a6e8ff285d87627af"
      },
      "source_reuse": true
    }
  ],
  "final_apk_hashes": {
    "dev.dexdroid": "ad0bbbf09afc7da18a3b00ca1f1942b4bcbfe737785e5aff74984045089189b6",
    "dev.dexdroid.test": "1dd8a2e6168bd212d955110ca1df3d8dd73057e394044cdc96476ca62a2815c1"
  },
  "reused_scope": "Workspace and monitor are final-binary executions. Phone UI, split-navigation and audio retain their original APK/source bindings, not reruns. Later changes include setup copy/labels, eligibility error normalization, per-request ordinary Single launch stability, fresh owned-display capture/reconciliation RPC and Save epoch/ownership invalidation. Shared UI style, pane input/geometry and audio algorithms are unchanged; final workspace522 exercises the new capture/launch boundaries and four-rotation Single/Split touch/Back; final monitor checks affected ordinary launch/input/empty controls. Historical launcher smoke establishes the same private entry and pin branch, not a fresh final-process timing measurement. Prior UI/split test artifact predates the isolated AudioWorker retry assertion, which those suite flags never execute.",
  "activation_samples_ms": [
    880,
    2835,
    931,
    2774,
    966,
    2858,
    964,
    2930,
    2792
  ],
  "activation_median_ms": 2774,
  "activation_max_ms": 2930,
  "timing_scope": "9 observed controlled activation-to-restored samples, desktop service/bridge warm; not end-to-end input/frame latency or cold app process distribution. Real launcher cold process outcome is separate.",
  "render_review": {
    "agent": "/root/workspace_render_review",
    "reviewed_at": "2026-10-26T17:21:04Z",
    "verdict": "Approve all12 final original captures within visible scope; explicit actual0/90/180/270 and pane arrangements. Pin-copy approval16:22:02Z remains separately source-bound reused scope.",
    "gaps": [
      "offscreen monitor Fit bottom",
      "collapsed cue pixels",
      "confirmation/cancel dialog absent",
      "assistive tech/large font/peripheral/acoustic/performance matrix"
    ]
  },
  "privacy": "All raw captures/dumps/binaries remain verified private local archives; public CI release archive is generated separately.",
  "post_regression_changed_inputs": [
    "app/src/androidTest/kotlin/dev/dexdroid/WorkspaceShortcutAcceptance.kt",
    "app/src/main/aidl/dev/dexdroid/IDeskService.aidl",
    "app/src/main/kotlin/dev/dexdroid/DeskService.kt",
    "app/src/main/kotlin/dev/dexdroid/DesktopTasks.kt",
    "app/src/main/kotlin/dev/dexdroid/SessionService.kt",
    "app/src/main/kotlin/dev/dexdroid/UserGuide.kt",
    "app/src/main/kotlin/dev/dexdroid/WorkspaceShortcut.kt",
    "app/src/main/kotlin/dev/dexdroid/WorkspaceShortcutPanel.kt"
  ],
  "withdrawn_claims": {
    "old_workspace_run": "20261026T161735.133138Z",
    "native_assertions": 408,
    "scope": "Only0-degree restoration was established. Old nonzero filenames and previous four-rotation claim are withdrawn; old bytes retained.",
    "release_architecture_verdict": "changes-needed",
    "reviewer": "/root/workspace_release_architecture",
    "reviewed_at": "2026-10-26T16:39:02Z",
    "rejected_digest": "2462acec9744f5beb23804a558822eb08636d251e53ad3cf045a38f7d9eba922"
  },
  "independent_rotation_checks": "Request rotation after fixture launch; assert loop index before save/after restore, explicit ratio/swap/selection/fullscreen and actual native app-window dimensions/input. Unavailable-app result asserts label + reinstall/update, no raw ComponentInfo.",
  "capture_race_resolution": {
    "rejected_final_digest": "0d047f99b96ad962054b50c3b2d623b585d5af67d8db5d58cf93bbbc6d703405",
    "reviewer": "/root/workspace_final_b",
    "reviewed_at": "2026-10-26T17:06:37Z",
    "outcome": "No publication approval from that pair. Per-launch guards, fresh native identities, terminal reconciliation and pending-Save mutation/Stop cancellation with final ownership validation now tested. Two491-assertion native streams retained as runner-label rejections, not successful outer runs."
  }
}
```
