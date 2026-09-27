# Implemented patches — 2026-03-26 round 6

Based on: `open-v31.5-main-patched-2026-03-26-round5.zip`

## Main Contacts screen navigation redesign
- Reworked the left-side drawer so it now focuses on browsing the active vault structure only.
- The drawer now shows:
  - Vaults list
  - All contacts (default view of the active vault)
  - Folders in the active vault
  - Tags in the active vault
- Selecting a folder or tag now filters the Contacts list directly in the main Contacts screen instead of sending the user to Workspace.
- Added counts for folders and tags inside the drawer.

## Settings relocation
- Moved vault/group management access into Settings home instead of keeping those management entries in the Contacts drawer.
- Added new Settings entries:
  - Vaults
  - Groups & Tags
- Added a dedicated Settings icon in the Contacts header for faster access.
- Replaced the bottom-bar "Groups" shortcut with "Settings" to match the new navigation model.

## Default Contacts behavior
- Contacts screen now defaults to showing all contacts from the active vault.
- Added an "All contacts" item in the drawer to clear any active folder/tag filter.
- Added an active filter info card above the tabs when a folder/tag filter is applied.

## Reset filter behavior
- Folder/tag filtering is now treated as temporary browsing state.
- When the Contacts screen resumes, the temporary folder/tag filter resets back to the default vault-wide Contacts list.
- This aligns with the requested behavior that moving around the app returns Contacts to the vault default view.

## Files changed
- `feature/contacts/src/main/java/com/opencontacts/feature/contacts/ContactsRoute.kt`
- `app/src/main/java/com/opencontacts/app/SettingsHomeRoute.kt`
