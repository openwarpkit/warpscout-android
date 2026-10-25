# Pending production changes

## WSA-2026-10-25-001

- Change: Port upstream registration API host update for WARP and MASQUE; include core commits in Android release notes.
- Repository: `openwarpkit/warpscout-android`.
- Code commits: `2bc84efc7c5811fe875a663384a627407daa1657`, `2fe283ff51cf5402a2fa6f52651c3209cf039baa`.
- Dependencies: none.
- Checks: `node scripts/check-upstream-sync.mjs`; `node scripts/check-readme.mjs`; `go test ./...`; `scripts/build-mobile.ps1`; Android debug unit tests, debug lint, debug APK, release lint, and release APK via `scripts/build-android-local.ps1`.
- Approved: 2026-10-25, [owner approval in this task](codex://threads/01a17498-db5b-7e30-bd9e-4b5372ff9b49).
