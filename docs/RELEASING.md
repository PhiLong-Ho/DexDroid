# Android releases

The **Release Android** GitHub workflow builds signed release APK/AAB files and
publishes them on a versioned GitHub release with concise changelog notes.

## Publish a version

1. Update `versionName` and increment `versionCode` in `app/build.gradle` for a new
   app version. Write a matching `## [<version>]` entry in CHANGELOG, with change
   bullets. The existing first GitHub release uses 0.10 / versionCode 15.
2. Update the spec/evidence as required by [CONTRIBUTING](../CONTRIBUTING.md), then
   commit and push the changes.
3. Push the matching version tag:

```sh
git tag -a v0.10 -m "DexDroid 0.10"
git push origin v0.10
```

Use the new version instead of 0.10 for subsequent releases. Alternatively, run
**Actions > Release Android > Run workflow** with an existing version tag that
contains the release workflow/tools. Manual dispatch uses the same checks.
Prerelease names such as `0.11-rc.1` produce prereleases.

## Gates and assets

The tag must match the checked-out source and literal app version. Its changelog
entry must exist exactly once and include at least one change bullet. Formatting,
lint, host tests, documentation links and requirement mappings must pass before
signing/building the release variants.

The workflow verifies APK package/version and non-debuggable status, APK/AAB
signatures and matching certificates, then publishes:

- `DexDroid-<version>.apk`: directly installable Android package.
- `DexDroid-<version>.aab`: Android app bundle for distribution tooling; not directly installable.
- `SHA256SUMS.txt`: SHA-256 hashes of both binaries and build metadata.
- `build.json`: version/code, source commit, repository, signature fingerprint and binary hashes.

Notes contain up to five change bullets from that version's changelog entry and a
link to the full changelog. Historical device verification is linked when present;
host/release checks do not claim a fresh device test. This remains an Android 11
sideload project; producing an AAB does not establish Play Store eligibility.

Assets are uploaded to a draft before publication. A failed draft can be retried;
an already published release is never replaced. For a correction, increment the
version/code, write new notes and push a new tag. Only the publish job has
`contents: write`. Build/evidence artifacts are retained by Actions for 14 days;
evidence uses `testresults/<version>/runs/release-<run-id>/`.

## Signing and backup

One persistent release key is configured in these repository Actions secrets:

| Secret | Purpose |
| --- | --- |
| `DEXDROID_KEYSTORE_BASE64` | Base64-encoded private JKS |
| `DEXDROID_KEYSTORE_PASSWORD` | Keystore password |
| `DEXDROID_KEY_ALIAS` | Release key alias |
| `DEXDROID_KEY_PASSWORD` | Key password |

The local backup is `.local/signing/dexdroid-release.jks`, with private values in
`.local/signing/release.properties`. Back up both securely outside the repository;
GitHub secrets cannot be downloaded later. `.local/` and keystores are ignored.
CI restores the key into runner temporary storage and removes it before uploading
artifacts. Secret values never belong in documentation, release files or logs.

Local Gradle reads the ignored properties file; environment variables with the
same names take precedence. `DEXDROID_KEYSTORE_PATH` points to the JKS. With these
configured, run:

```sh
./gradlew :app:assembleRelease :app:bundleRelease
```

The existing development/debug install has a different signing certificate.
Android cannot replace it in place with this release-signed APK; plan a settings/
data migration before replacing a debug install. Subsequent release APKs reuse
the same release key for normal updates. Publishing does not change the phone install.
