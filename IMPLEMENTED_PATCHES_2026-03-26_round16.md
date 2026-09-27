# Round 16 — Arabic, Fonts, and Dial Pad UI

## What changed

### 1) Broader Arabic UI support
- Added a localization helper layer in `core/ui/localization/Localization.kt`.
- Added a large Arabic phrase map plus dynamic translation rules for common UI phrases.
- Wired localization into the main settings UI components so titles, subtitles, values, and chips translate automatically when Arabic is active.
- Localized major visible areas in:
  - Contacts screen
  - Contact details screen
  - Appearance screen
  - Preferences screen
  - Vaults screen
- Localized many chooser titles and QR/share labels as well.

### 2) App-wide font system
- Added persisted app font settings:
  - `appFontProfile`
  - `customFontPath`
  - `customFontDisplayName`
- Added font controls in `Appearance`:
  - choose from multiple built-in font profiles
  - import a custom TTF / OTF font from the phone
  - clear the custom uploaded font
- Theme plumbing now passes font settings into `OpenContactsTheme` so the chosen font applies app-wide.

### 3) Dial pad UI adjustment
- Kept the hide/show toggle behavior, but removed the visible `Hide` text.
- Kept the circular hide handle as the visual dismiss control.
- The dial pad now uses the circle/handle in the requested position instead of the old text label.

## Important note
- This is a source-level patch set. I did not run a full Android build in this environment.
- The Arabic support is now much broader on the main UI surfaces, but app-wide localization depth still depends on how many raw hardcoded strings remain in less-used screens.
