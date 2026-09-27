# Release readiness checklist

- Build `release` with a real keystore through environment variables:
  - `OPENCONTACTS_RELEASE_KEYSTORE`
  - `OPENCONTACTS_RELEASE_STORE_PASSWORD`
  - `OPENCONTACTS_RELEASE_KEY_ALIAS`
  - `OPENCONTACTS_RELEASE_KEY_PASSWORD`
- Request the dialer role from inside the app and verify `Default phone app` is granted.
- Verify incoming call presentation in these states:
  - app foreground
  - app background
  - home screen
  - other apps
  - lock screen
- Verify notification permission on Android 13+.
- Verify overlay permission only if the overlay mode is enabled.
- Validate backup creation, restore latest, restore from file, and import/export round-trip.
- Test Arabic and English.
- Test icon alias switching on the target launcher.
- Review Play policy scope for permissions related to call handling and contacts.
