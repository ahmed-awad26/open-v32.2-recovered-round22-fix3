Round 15 changes

- Added a dedicated calling preference to switch between OPENCONTACTS_DEFAULT_APP and SYSTEM_PHONE_APP.
- Added a direct "Set OpenContacts as default phone app" action inside Preferences.
- Call button behavior now reads the saved mode from bootstrap preferences so all call buttons follow the same behavior immediately.
- Improved default dialer qualification by adding a plain ACTION_DIAL intent filter alongside the tel: handler on DialerEntryActivity.
- Added explicit handoff-to-system-phone-app behavior using ACTION_DIAL, preferring TelecomManager.getSystemDialerPackage() when available.
