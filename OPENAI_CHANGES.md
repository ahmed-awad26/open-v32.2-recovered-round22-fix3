# OpenAI changes applied

## 1) Professional alphabet fast scroller

New files:
- `feature/contacts/src/main/java/com/opencontacts/feature/contacts/fastscroll/AlphabetSectionIndex.kt`
- `feature/contacts/src/main/java/com/opencontacts/feature/contacts/fastscroll/TouchLetterMapper.kt`
- `feature/contacts/src/main/java/com/opencontacts/feature/contacts/fastscroll/AlphabetFastScroller.kt`

Updated integration:
- `feature/contacts/src/main/java/com/opencontacts/feature/contacts/ContactsRoute.kt`

What changed:
- Replaced the old wide button-based alphabet rail with a narrow drag-enabled fast scroller.
- Built the alphabet index once per filtered/sorted list with `remember(filteredContacts)`.
- Added `#` handling for blank/non-Latin/non-alphabetic names.
- Added nearest-available-letter fallback when a letter has no matching section.
- Uses immediate `scrollToItem()` instead of slow animated scrolling.
- Added active letter bubble only while dragging.
- Reduced visual width while keeping a wider touch target.

## 2) Backup / export folder selection via SAF

New class:
- `data/repository/src/main/java/com/opencontacts/data/repository/TransferDestinationManager.kt`

Updated storage settings:
- `core/crypto/src/main/java/com/opencontacts/core/crypto/AppLockRepository.kt`
- `app/src/main/java/com/opencontacts/app/AppViewModel.kt`
- `app/src/main/java/com/opencontacts/app/SecurityRoute.kt`

Updated backup/export repository flow:
- `data/repository/src/main/java/com/opencontacts/data/repository/VaultTransferRepositoryImpl.kt`
- `data/repository/src/main/java/com/opencontacts/data/repository/GoogleDriveBackupAdapter.kt`
- `data/repository/src/main/java/com/opencontacts/data/repository/OneDriveBackupAdapter.kt`
- `app/src/main/java/com/opencontacts/app/BackupRoute.kt`
- `app/src/main/java/com/opencontacts/app/ImportExportRoute.kt`
- `data/repository/build.gradle.kts`

What changed:
- Added persistent SAF tree-URI storage for backup and export folders.
- Supports different folders for backup and export.
- Keeps fallback internal app-storage folders when user resets or never selects a folder.
- Writes backup/export files into the chosen SAF folder using `DocumentFile` + `ContentResolver`.
- Reads latest backup/export from the selected folder when needed.
- Handles missing folder/permission failures with user-facing error messages.
- Keeps CSV/VCF import-by-picker flow working through `vault_imports` fallback staging.

## 3) Removed automatic tag creation during photo update

Updated file:
- `data/repository/src/main/java/com/opencontacts/data/repository/ContactRepositoryImpl.kt`

Root cause fixed:
- The repository was parsing hashtags from `displayName` on every save.
- Photo updates call `saveContactDraft()`, so photo-only edits could still trigger tag generation.

What changed:
- Removed automatic hashtag extraction from `displayName`.
- Replaced it with explicit-tag normalization only.
- Photo update flow now keeps existing tags exactly as-is and does not create extra tags indirectly.
- This also prevents the same side effect during create/edit/import unless tags are explicitly supplied.

## Build note

This container did not include a real Gradle wrapper nor a `gradle` executable inside the project, so a full build could not be executed here.
The project bundle below contains all source changes, ready to open locally in Android Studio or your own CI environment.
