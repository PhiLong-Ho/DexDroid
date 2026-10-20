# Kotlin migration phase 1

The pinned Kotlin 2.2.21 build and ktfmt 0.58 formatter compiled the first helper
and its three JVM tests. All 148 JVM cases passed. Tooling: 169 passes and one
Windows platform skip. Kotlin-aware coverage and production exclusion guards
passed, including synthetic APK/AAB leakage rejection. No lint baseline changed.

API 30, 36 and 37 each passed input, keyboard and idle/Stop smoke suites: nine
selected suites, not complete regression. API 37 used `swiftshader_indirect`;
the published host-renderer platform failure remains experimental. No physical
phone was used in this phase. Hardware keyboard, OEM and complete visual/performance
acceptance remain outside this checkpoint.

The verified [archive descriptor](archive.json) identifies the private local bundle
containing exact source/APK hashes, configuration, raw outcomes and JVM results.
Staged checkpoint tree: `552b47141ff90e6ada22a6236b124d7321d72739`.
Runs span `20261018T083749.366434Z` through `20261018T084455.372824Z`.
The archive is local-only; no device capture was uploaded.
