# Round 8 patches

Applied on base archive: `open-v31.5-main-patched-2026-03-26-round7.zip`

## Contacts main screen
- Removed the bottom bar from `ContactsRoute`.
- Added a floating green dial-pad button to open/close the existing dial pad from the main screen, matching the requested video behavior more closely.
- Kept the current dial pad UI itself intact.
- Moved the `Favorites` filter from the removed bottom bar into the left drawer.
- Added an `Add contact` action to the top search/header row so the add flow remains accessible after removing the bottom bar.

## Drawer behavior
- Added a dedicated `Favorites` drawer item.
- `All contacts` now explicitly returns to the normal vault-wide view and turns off the favorites-only state.

## App icon unification
- Added a new vector app logo based on the attached reference image:
  - `app/src/main/res/drawable/ic_app_logo_vector.xml`
  - `core/ui/src/main/res/drawable/ic_app_logo_vector.xml`
- Switched the application icon and all launcher alias icons in `AndroidManifest.xml` to this vector logo.
- Updated legacy alias drawables to point to the same unified vector logo.
- Replaced generic fallback app icon usage in the floating incoming-call layout/service with the new logo.
- Added a reusable Compose logo component:
  - `core/ui/src/main/java/com/opencontacts/core/ui/AppLogo.kt`

## UI places updated to use the new logo
- Lock / unlock screen.
- Launcher icon customization preview fallback.
- Dashboard header.
- Floating incoming call fallback avatar.

## Notes
- The requested “SVG” icon was implemented as Android **vector drawable XML**, which is the Android-native scalable equivalent used for sharp rendering in-app and in the launcher.
