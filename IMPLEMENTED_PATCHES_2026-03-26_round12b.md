# Implemented patches — 2026-03-26 round12b

Based on `open-v31.5-main-patched-2026-03-26-round11-hotfix3.zip`

## Done
- Added a new saved preference: `drawerFolderDisplayMode`
  - `LIST` keeps the current drawer folder list layout.
  - `CAROUSEL` shows folders as a horizontal image strip with swipe support.
- Added `Folder drawer layout` chooser under `Preferences > Groups, folders & tags`.
- Updated the contacts side drawer:
  - folders can render in the new carousel/image mode
  - tags now live inside a bounded vertical scroll region
- Kept the fixed bottom drawer area for:
  - Settings
  - Dark / Light mode toggle
- Unified launcher icon usage across launcher-facing surfaces:
  - application icon
  - round icon
  - launcher aliases
- Added adaptive launcher icon resources:
  - `mipmap-anydpi/ic_launcher.xml`
  - `mipmap-anydpi/ic_launcher_round.xml`
  - `mipmap-anydpi-v26/ic_launcher.xml`
  - `mipmap-anydpi-v26/ic_launcher_round.xml`
  - `drawable/ic_launcher_background.xml`
  - `drawable/ic_launcher_foreground.xml`
- Removed `Launcher icon` from the Settings home menu.
- Removed the `settings/icon` route from `MainActivity` navigation graph.
- Preserved the previous `round11-hotfix3` dial pad overlay fix by using `fillMaxSize()` instead of the unresolved `matchParentSize()` call.

## Main files changed
- `feature/contacts/src/main/java/com/opencontacts/feature/contacts/ContactsRoute.kt`
- `app/src/main/java/com/opencontacts/app/PreferencesRoute.kt`
- `app/src/main/java/com/opencontacts/app/SettingsHomeRoute.kt`
- `app/src/main/java/com/opencontacts/app/AppViewModel.kt`
- `app/src/main/java/com/opencontacts/app/MainActivity.kt`
- `core/crypto/src/main/java/com/opencontacts/core/crypto/AppLockRepository.kt`
- `app/src/main/AndroidManifest.xml`
- launcher icon resource files under `app/src/main/res/`
