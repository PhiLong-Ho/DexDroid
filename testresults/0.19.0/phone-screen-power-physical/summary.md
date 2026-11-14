# Physical phone wake trials — 2026-11-13

Post-release feasibility only, against installed 0.19.0 (52), baseline
`53bf44a5bb9e04433d143feaa593137ed863f1e8`. No product source/test or production
package changes. [Contract/review and interpretation](../../../docs/changes/phone-screen-power.md),
[earlier injected proof](../phone-screen-power-prototype/summary.md).

Device: Xiaomi 14 / Android 16 with physical 1920×1200 monitor and phone touch.
No broad Android/OEM, rotation, split, Fit, keyboard or performance claim.

| Scenario | Evidence/outcome |
| --- | --- |
| Physical double tap | `guard-manual-20261113T142151Z`: OFF then automatic RESTORED, no CONTACT/WAKE. User confirms tapping had no effect until test ended. Failed on this approach/device |
| First Power attempt | `20261113T142328Z`: timed out before Start; no OFF admitted, no Power result |
| Physical Power | `guard-manual-20261113T142500Z` and `physical-power-20261113T142500Z`: actual KEY_POWER DOWN/UP pairs; system sleep reason power_button, keyguard and external Off, later wake. User confirms Power immediately turns phone off. Failed phone-only Power behavior |
| Recovery/cleanup | Finite same-token NORMAL/watchdog restores; fixture uninstalled; production controller reopened. Final phone/monitor SurfaceFlinger On and OS Awake |

The guard's Power-trial exit 0 means its observation/restore procedure completed,
not that the feature passed. One CONTACT in that trial is not a double tap and its
relation to physical panel transition is undetermined. User wake/unlock is separate
from helper restoration; no synthetic Power, keyguard dismissal or security-setting
change. Physical double-tap raw touch-node tracing was not performed, so driver
versus dispatcher responsibility is unknown. No hardware power-off/reboot occurred:
ADB remained connected and Android reports sleep/wake events.

Private archive: [descriptor](archive.json), 185 files, local-only at
`artifacts/evidence/phone-screen-power-physical-20261113/evidence.zip`, SHA-256
`c0d8467d66c6de5e8b00119a70a9aaf76fe7cff5219276a8bf2ebfdf5ea0e247`.
ZIP/member/provenance verification passes. Retain locally until explicitly removed;
no upload or external retention guarantee. Contains private device dumps, captures,
temporary sources/APKs and current/failure attempts, not public evidence.

Current helper/fixture hashes remain those in the earlier summary; the current
private broker extends only the pre-Start waiting window to five minutes. The
on-device OFF restoration deadline remains ten seconds. The earlier sealed archive
is retained unchanged. Product coverage remains deferred; no production implementation,
UI placement decision, lifecycle/death recovery acceptance or release approval.
