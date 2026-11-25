# Deployment log

## 2026-10-25: Android v1.5.2

- Change ID: `WSA-2026-10-25-001`.
- Repository: `openwarpkit/warpscout-android`.
- Code commits: `2bc84efc7c5811fe875a663384a627407daa1657`, `2fe283ff51cf5402a2fa6f52651c3209cf039baa`.
- Release source commit: `cd6338534461b77a5796481ecce5c08d67955d88`.
- Release tag: `android-v1.5.2`, tag object `2fe280031a17fa3b91110312d377f35c28e72d9c`.
- GitHub Actions run: [40228774509](https://github.com/openwarpkit/warpscout-android/actions/runs/40228774509), succeeded.
- GitHub Release: [v1.5.2](https://github.com/openwarpkit/warpscout-android/releases/tag/android-v1.5.2), ID `423635190`, published 2026-10-25 23:11 MSK.
- Artifacts: four ABI/universal APKs, `SHA256SUMS`, and `THIRD_PARTY_NOTICES.md`.
- Result: published and verified through the GitHub release API.

## Android v1.6.0 (2026-11-25)

- Changes `WSA-2026-11-25-001`, `WSA-2026-11-25-002`, and `WSA-2026-11-25-003`.
- Repository `openwarpkit/warpscout-android`.
- Code commits `d0a573ff00832cd6b3a75c94b1a9cddaefe98aff`, `a127a7434b08eb63508b2983fd65ac622832ca5d`, and `14c372722c1768972d8a6ed4b67016a9de9dca7d`.
- Release source commit `14c372722c1768972d8a6ed4b67016a9de9dca7d`.
- Release tag `android-v1.6.0`, tag object `b842ffba7c7d2678eb0bfd522b859f93f4641234`.
- Candidate CI [45690644126](https://github.com/openwarpkit/warpscout-android/actions/runs/45690644126) passed.
- Release workflow [45692447963](https://github.com/openwarpkit/warpscout-android/actions/runs/45692447963) passed, including launch of the signed universal APK on the emulator.
- GitHub Release [v1.6.0](https://github.com/openwarpkit/warpscout-android/releases/tag/android-v1.6.0), ID `458607285`, published 2026-11-25 23:21:04 MSK.
- Published assets with GitHub IDs and SHA-256 digests.
  - `SHA256SUMS`, ID `793007535`, SHA-256 `22622a5efe961f7f39e60def27b11c6ed8fae6d0de166b30ca433ca054fe3139`.
  - `THIRD_PARTY_NOTICES.md`, ID `793007529`, SHA-256 `af5878d89fe4df1494fafb15558f5b5d9aa9747e44594b765aa88335ff0bf5cc`.
  - `warpscout-android_1.6.0_arm64-v8a.apk`, ID `793007530`, SHA-256 `997b0e551ade6d957c1435791cd4dbbddb39526f5d1bfa3feafea49559b4b173`.
  - `warpscout-android_1.6.0_armeabi-v7a.apk`, ID `793007532`, SHA-256 `04fd118b588d67813f497b0ef2abae4b5816c64e6758c083afea42988a1188d5`.
  - `warpscout-android_1.6.0_universal.apk`, ID `793007524`, SHA-256 `819fd0a0c6aa536c15123a101cb1405e97998ef901ea5e125e08312a646c8c30`.
  - `warpscout-android_1.6.0_x86_64.apk`, ID `793007539`, SHA-256 `11929692f7006eaa7848cf6c4cb432faae4c3f3c585335b6c83e5c2daeb30da6`.
- All four APKs use the v1.5.2 signing certificate. The APK and notices digests match `SHA256SUMS`.
