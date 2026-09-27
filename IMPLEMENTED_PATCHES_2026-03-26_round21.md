Implemented on top of open-v31.5-main-patched-2026-03-26-round20-hotfix-contacts1.zip

Changes:
- Added SIM-aware call flow support.
- Added ask-every-time SIM chooser before placing a call when multiple SIMs are available.
- Added default SIM selection from Preferences.
- Added SIM chooser opacity and size controls in Preferences.
- Added centered transparent SIM chooser activity.
- Added USSD support by encoding * and # correctly and routing through ACTION_CALL / Telecom flow.
- Synced new call/SIM settings into DataStore and bootstrap shared preferences.
- Included new SIM chooser settings in raw settings export/import snapshots.
- Registered SimChooserActivity in AndroidManifest.
- Added Arabic localization entries for new SIM-related strings.

Notes:
- No intermediate review artifacts were included.
- This is a source-level patch and was not fully build-verified inside the current environment.
