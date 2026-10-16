# Change: Symmetric status and touchpad controls

## Contract and clarified choices

FR-001 / DX-UI-001 was updated before implementation. The user confirmed the
dropdown belongs at the bottom center of status, below monitor and Start/Stop,
and Save/Reset belongs in Settings > App settings after removing the triple-dot
shortcut. The phone centers Touchpad with adjacent Info between Scroll and Back.
The shared design rules require clarification of ambiguous design decisions unless
the user delegates them. No new requirement ID or unrelated feature is introduced.

## Implementation

The shared StatusCard removes the overflow action and places the existing 48 dp
expand target as its final centered child. Exceptional notices and confirmation
remain outside hidden details. Both Settings menus have one App settings entry
near the top with the selected app name; nested Up returns to Settings. The phone
pad header centers a label/Info group between equally sized edge actions. Header
contacts remain isolated from pad gestures. No input transport or lifecycle
ownership changes. Guide, README and design/architecture docs use the new locations.

## Acceptance and evidence

UiAcceptance checks status/footer bounds, no overflow, symmetric nonoverlapping
header groups and accessible targets. PhoneMenuAcceptance exercises Settings >
App settings > Scrolling > Up, Save/Reset and Up to Settings. MonitorAcceptance
uses the same path and checks shared geometry in four rotations, alongside its
existing Fit, input and lifecycle assertions. Existing concrete requirement checks
remain mapped; generated coverage is refreshed with all changed tests.

Actual commands, APK/source hashes, current results, attempts, rendered inspection
and gaps are retained in [the evidence record](../../testresults/0.12.0/symmetric-controls/README.md).
Human visual review and performance evidence are separate from assertions. Prior
feature evidence remains historical. No publication or release is implied.
