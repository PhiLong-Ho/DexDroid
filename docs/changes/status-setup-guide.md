# Change: Collapsible status, Shizuku setup and offline guide

## Contract (written before implementation)

- DX-UI-001 / FR-001: compact status by default, shared remembered expansion;
  header actions remain accessible; preview, protection and recovery stay visible.
- DX-SETUP-001 / FR-034: explicit install/open/authorize/connect guidance, honest
  non-root startup/reboot limits, display-0 external navigation and useful errors.
- DX-GUIDE-001 / FR-035: one concise offline full-feature guide in both Settings
  menus, related-help Up returns to guide with scroll preserved, X closes all.
- Updated baseline/design and new [feature spec](../specs/setup-and-guide.md)
  before implementation. Requirement checks/gaps and generated coverage accompany
  every test revision. No hidden ADB client or silent privileged startup is added.

## Implementation

- Shared `StatusCard` hides only details. Header contains monitor, desktop/app
  actions and arrow; service preferences preserve/synchronize expansion. Expanding
  reduces available pad space; collapsing returns it. Exceptional controls are
  outside the hidden region.
- Service caches Shizuku availability, authorization and bridge state. Foreground
  checks refresh it; existing authorization/reconnect flow owns privileged work.
- `ShizukuSetup` provides shared native instructions/state actions. Manager and
  official download destinations launch only on user action and target the phone;
  errors preserve the setup page. Package visibility explicitly names Shizuku.
- `UserGuide` owns the shared offline text; surface menu owners use existing
  navigation and styling. Guide content covers current single/split/input/display/
  profile/lifecycle/help features and their limitations.

## Acceptance

| Criterion | Checks | Scope and exclusions |
| --- | --- | --- |
| Compact/expanded geometry and durable preference | UiAcceptance, MonitorAcceptance | Native phone/pad geometry and monitor menu rebuilds; full process-death persistence is not inferred |
| Setup dependency actions and external-handler errors | ShizukuSetupTest, SetupPanelAcceptance | Pure classification plus controlled native panel state/navigator fixtures; no real uninstall/stop/permission grant |
| Guide content, related Up and parent position | PhoneMenuAcceptance, MonitorAcceptance | Phone plus four monitor rotations; screenshots separate from assertion outcomes |
| Existing display/input/lifecycle invariants | Connected suites | Only actually executed suites/configurations count; modern Android/peripheral/fault breadth remains scoped |

Commands, exact source/APK hashes, run/configuration, actual outcomes, inspected
captures, measured timing boundaries, attempts and gaps belong in
[the retained evidence (evidence withdrawn)](../../testresults/README.md). Current historical release evidence is
not a pass for this changed build. No lint baseline change, release/version bump
or remote publication is part of this task.

Before committing, stage final evidence and obtain exactly two fresh independent
reviews of the candidate under [the review protocol](../REVIEWS.md). Actual
identities and final digest belong in `reviews/approvals.json`. Human visual
review, pixel goldens and full physical-performance evidence remain separate.
