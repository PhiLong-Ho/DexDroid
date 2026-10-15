# Apps responsiveness and Chrome protected windows

## Contract (recorded before implementation)

FR-026 / DX-CATALOG-001 requires immediate shared-style menu acknowledgment,
background discovery, visible pending/retry, cached warm opens, current-query
retention and cleanup on dismissal. FR-027 / DX-SECURE-001 documents protected
transport limits without silently changing another app's privacy settings.

The report was a blank Chrome incognito window plus intermittent Apps stalls on
Samsung Android 16. The user requested research on direct Samsung DeX and optional
self-service Chrome flag instructions if this is a platform limit. Secure output,
root privileges and changing Chrome configuration are excluded.

## Implementation

Build 23 moves launcher discovery/labels/icons into a service-owned single-worker
AppCatalog with revision invalidation, shared cache and bounded subscriptions.
Both menus use ControllerStyle's monochrome pending/error row and keep existing
search editors while results arrive. Icon bounds are independent per surface.
Package broadcasts invalidate the cache; errors preserve previous entries and
allow retry. Dismissed menus ignore late results.

The reported Chrome window has FLAG_SECURE; the authorized shell lacks secure
capture permission. Android's protected virtual-display rule explains blank
output. Banking apps can have different per-window policies. Samsung's browser
team confirms direct-monitor/tablet Secret mode versus PC-mirroring limitations
for Samsung Internet; Chrome-specific DeX behavior is not established.
[Troubleshooting](../TROUBLESHOOTING.md) contains the optional flag, its privacy
consequence, undo steps and primary sources. No Chrome flag was changed.

## Acceptance and remaining gaps

See the [scoped build-23 report](../../testresults/0.11.1/apps-loading/README.md)
for exact APK/source hashes, host results, device runs, retained failures,
representative captures and measured timing boundaries. A catalog test holds a
real source on its worker to check pending, cache/search refresh, retry, four
rotations and dismissal. Phone/monitor functional UI checks remain separate.

The first full regression found a test-side unsynchronized display-reference
read. It now snapshots on the main thread without removing connectivity/input
assertions. A later broader run dropped a rotation-1 targeted tap; it remains a
failed run. No full-regression or physical-input latency guarantee is claimed.
Human visual approval, the full performance protocol, broader OS/hardware and
fault-injection scenarios remain open. No lint baseline changed. No push or
release occurred; publication still requires the repository's two fresh reviews.
