# Upstream synchronization

## Repositories

- Origin: https://github.com/openwarpkit/warpscout-android
- Upstream: https://github.com/vernette/warpscout

## Current base

- Upstream tag: `v0.16.0`
- Upstream commit: `db4ac9ebae8d942191b8e8351f2c3a37a375bd66`
- Upstream branch: `master`

The current base matches upstream tag `v0.16.0`.

## Branch roles

- `android`: default branch for the Android application and releases.
- `main`: upstream synchronization branch that tracks the original CLI code line.
- `chore/sync-upstream-<version>`: temporary integration branch created from `android`.

Android-specific changes are not committed directly to `main`.

## Update procedure

1. Fetch tags and `upstream/master`.
2. Update `main` from `upstream/master` without adding Android-specific changes.
3. Create `chore/sync-upstream-<version>` from `android`.
4. Merge `main` into the synchronization branch with a merge commit. Do not rebase or squash upstream history.
5. Resolve conflicts without changing the base relay behavior or the Android application ID.
6. Update the tag and commit in this file, Android build defaults, and release metadata.
7. Run `go test ./...`.
8. Build the Go Mobile AAR.
9. Run Android unit tests, lint, Room schema validation, and the emulator smoke test.
10. Merge the synchronization branch into `android` only after Go and Android CI pass.

## Automated validation

Run the local contract check before building:

```sh
node scripts/check-upstream-sync.mjs
```

For a synchronization branch, compare the recorded commit with the fetched upstream head:

```sh
node scripts/check-upstream-sync.mjs --require-ref upstream/master
```

Android CI performs the structural check on every change. Pull requests whose branch name starts with `chore/sync-upstream-` are also compared with the current `upstream/master` commit. The check rejects unexpected root Go files, stale upstream metadata, a missing mobile backend, forbidden process exits across the mobile boundary, and an upstream commit outside the current history.

CLI releases continue to use `v*` tags. Android releases use `android-v*` tags. CLI release workflows must exclude `android-v*`.
