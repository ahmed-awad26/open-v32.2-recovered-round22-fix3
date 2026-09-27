# Implemented patches — 2026-03-26 round 4

## What was fixed

### 1) Tags keep supporting multiple contacts
- Kept tag behavior as **many contacts under the same tag**.
- Did **not** reintroduce replacement logic.
- Same tag can remain attached to many contacts normally.

### 2) Vault default selection is now real and persistent
- Added a real `isDefault` flag to the vault registry data model.
- Added Room migration `2 -> 3` for the vault registry.
- The chosen default vault is now stored and survives app restarts.
- On startup, the app now restores the **default vault**, not just the first vault it finds.
- Creating a new vault no longer steals focus automatically unless the user chooses **Use now**.

### 3) Add / Change Vault screen was rebuilt into a real manager
- Reworked `VaultsRoute` into a functional management screen.
- Added:
  - **Use** any vault
  - **Make default**
  - **Edit** vault name
  - **Lock** vault
  - **Quick add contact** into that vault directly
- Vault cards now show clearer state:
  - Default
  - Active
  - Locked / Unlocked

### 4) Add contact to any vault without opening it first
- The visible "Add contact" action now works for each vault.
- Added a dedicated **quick add contact dialog** bound to the selected vault.
- You can save a contact directly into another vault without switching the whole app to it.
- Supports:
  - name
  - primary phone
  - extra phone numbers (one per line)
  - favorite toggle

### 5) Active/locked behavior fixed
- Previously selecting a locked vault could effectively unlock it through the UI flow.
- Now selecting a locked vault keeps it locked and routes correctly through vault session state.

### 6) Delete vault workflow improved
- Added **long-press selection** for a vault card.
- Added a highlighted delete panel once a vault is selected.
- Added explicit **confirmation** before deletion.
- Deleting the default vault now reassigns a fallback default automatically.
- Deleting the last vault recreates a safe fallback vault so the app is never left without a vault.

### 7) Backup / restore metadata kept in sync
- Vault metadata export/import now also carries the new default-vault flag.

## Main files changed
- `core/model/src/main/java/com/opencontacts/core/model/Models.kt`
- `domain/vaults/src/main/java/com/opencontacts/domain/vaults/VaultRepository.kt`
- `data/db/src/main/java/com/opencontacts/data/db/entity/VaultRegistryEntity.kt`
- `data/db/src/main/java/com/opencontacts/data/db/dao/VaultRegistryDao.kt`
- `data/db/src/main/java/com/opencontacts/data/db/database/VaultRegistryDatabase.kt`
- `data/db/src/main/java/com/opencontacts/data/db/database/DatabaseModule.kt`
- `data/db/src/main/java/com/opencontacts/data/db/mapper/Mappers.kt`
- `data/repository/src/main/java/com/opencontacts/data/repository/VaultRepositoryImpl.kt`
- `data/repository/src/main/java/com/opencontacts/data/repository/RoundTripTransferPackageCodec.kt`
- `data/repository/src/main/java/com/opencontacts/data/repository/VaultTransferRepositoryImpl.kt`
- `feature/vaults/build.gradle.kts`
- `feature/vaults/src/main/java/com/opencontacts/feature/vaults/VaultsRoute.kt`
- `feature/vaults/src/main/java/com/opencontacts/feature/vaults/VaultsViewModel.kt`
- `app/src/main/java/com/opencontacts/app/AppViewModel.kt`

## Important note
- I still could not run a full Android build in this environment because there is no usable Gradle wrapper / Android build runtime here.
- The changes were made directly in the source tree and cross-checked for consistency, but final compile verification should be done in Android Studio or CI.
