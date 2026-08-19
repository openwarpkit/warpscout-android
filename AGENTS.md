# Project workflow

## Android local build

Always build and install the Android application through `scripts/build-android-local.ps1`.

Do not run `android/gradlew.bat` directly from the repository path. The repository path contains non-ASCII characters, which can leave incompatible absolute paths in KSP and Gradle incremental state.

The script provides a persistent ASCII-only junction in the system temporary directory, uses the project SDK and Gradle cache, runs Gradle without a reusable daemon, and sets `WARPSCOUT_DEBUG_KEYSTORE` to `.cache/debug.keystore` when that file exists.

Build and test:

```powershell
.\scripts\build-android-local.ps1
```

After changing Go code in `core/`, `internal/warpscout/`, or `mobileapi/`, rebuild the Android AAR before Gradle:

```powershell
.\scripts\build-mobile.ps1
.\scripts\build-android-local.ps1
```

Build, test, and install without clearing application data:

```powershell
.\scripts\build-android-local.ps1 -Install -Serial <adb-serial>
```

Run Android lint:

```powershell
.\scripts\build-android-local.ps1 -Tasks :app:lintDebug
```

Use `-Clean` only when a clean rebuild is required. Do not uninstall the debug package to resolve a signature mismatch.
