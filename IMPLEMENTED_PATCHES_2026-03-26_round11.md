# Implemented patches — 2026-03-26 round11

## Dial pad improvements
- Increased the show/hide dial pad button slightly by default.
- Added settings to control dial pad toggle button size.
- Kept the dial pad toggle button visible while the dial pad is open so the same button can hide it again.
- Reworked the dial pad from a modal bottom sheet into a persistent bottom panel so the toggle button can remain accessible.
- Added a Hide action inside the dial pad panel as a secondary close method.

## Backspace behavior
- Added a configurable long-press duration for the dial pad delete button.
- Long-press delete now clears the full typed number after the configured hold time.
- Single tap still deletes one digit.
- Added an inline hint inside the dial pad about the current hold duration.

## Settings persistence
- Added new settings keys for:
  - dial pad toggle button size
  - dial pad backspace long-press duration
- Wired the new settings through:
  - AppLockRepository
  - AppViewModel
  - PreferencesRoute
  - settings snapshot export/import

- Hotfix build fix: replaced `matchParentSize()` overlay usage in `ContactsRoute.kt` with `fillMaxSize()` and removed the incompatible import causing CI failure.
