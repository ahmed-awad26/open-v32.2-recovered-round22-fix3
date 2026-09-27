# Phase 2 patches completed — 2026-03-25

## What was added

### 1) Multi-phone support per contact
- Added `ContactPhoneNumber` model.
- Added `phoneNumbers` to `ContactSummary` and `ContactDraft`.
- Added normalization helpers so duplicated/blank numbers are cleaned consistently.
- Added Room column `phone_numbers_json` to `contacts`.
- Added DB migration **8 -> 9** for the new phone list column.
- Updated DB mappers to read/write the full phone list while keeping `primaryPhone` aligned to the first number.
- Updated CSV/VCF import and export to preserve multiple phone numbers.
- Updated contact editor UI in both list/details flows with:
  - Primary phone field
  - Additional phones field (one per line)
- Updated call-log matching to match against **all** stored numbers.
- Updated search/T9 filtering to work better with multiple numbers.
- Updated QR / vCard / text sharing to include all stored numbers.
- Updated merge/import flows so phone arrays are not dropped during package or backup restore work.
- Updated bulk workspace/tag/folder operations so they preserve all phone numbers instead of rewriting the contact with only the primary number.

### 2) Backup / restore now includes app settings more fully
- Backup payload now includes an `appSettings` snapshot exported from `AppLockRepository`.
- Added export/import helpers in `AppLockRepository` for raw DataStore + related SharedPreferences snapshots.
- Restore now re-applies the saved settings snapshot after DB/media restoration.
- Backup now includes media referenced by settings where available, currently:
  - lock screen background
  - app icon preview image
- Restore rewrites those media references to the restored local copies before applying settings.

### 3) Extra consistency fixes
- Preserved multi-number data during:
  - tag replacement flows
  - folder assignment/removal flows
  - social link updates
  - contact edits from details screen
  - workspace batch operations
- CSV export now writes the first phone from the normalized phone list rather than relying on possibly stale `primaryPhone`.

## Files touched
- `core/model/.../Models.kt`
- `data/db/entity/ContactEntity.kt`
- `data/db/database/VaultDatabase.kt`
- `data/db/database/VaultDatabaseFactory.kt`
- `data/db/mapper/Mappers.kt`
- `data/repository/VcfCsvCodec.kt`
- `data/repository/RoundTripTransferPackageCodec.kt`
- `data/repository/VaultTransferRepositoryImpl.kt`
- `core/crypto/AppLockRepository.kt`
- `feature/contacts/ContactsViewModel.kt`
- `feature/contacts/ContactsRoute.kt`
- `feature/contacts/ContactDetailsRoute.kt`
- `feature/contacts/CallLogSupport.kt`
- `app/.../WorkspaceRoute.kt`

## Verification status
- I verified the project structure and cross-file consistency after the edits.
- I also verified that the included `gradlew` file is only a placeholder wrapper that delegates to a system `gradle` binary.
- In this environment:
  - `gradle` is not installed
  - the project does not include a full generated Gradle wrapper
- Because of that, I could not run a full Android build here.

## Remaining limitation
- This was implemented as a deep code patch against the uploaded source tree, but without a full Android build toolchain in the archive/environment I cannot guarantee zero compile/runtime issues until you build it in Android Studio or CI with a real Gradle wrapper.
