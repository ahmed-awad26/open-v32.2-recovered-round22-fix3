# Round 7 — Import/Export ZIP Bundle + Vault Selection

## Added
- New **Portable ZIP bundle** export/import flow in Import & Export screen.
- ZIP archive now carries:
  - full contact dataset
  - contact photos
  - folders
  - folder images
  - tags
  - notes
  - reminders
  - timeline
  - stable IDs and relation maps
- New **vault selection** section at the top of Import & Export.
  - export runs from selected vault
  - import runs into selected vault
  - recent history panel follows selected vault

## Technical design
- ZIP bundle is a separate format from the existing `.ocpkg` package.
- Archive structure:
  - `manifest.json`
  - `payload/package.ocpkg`
  - `media/index.json`
  - `media/contacts/...`
  - `media/folders/...`
  - `README.txt`
- The embedded package preserves stable contact IDs / tag refs / folder refs.
- Media is indexed by unique keys:
  - contact photo -> `contactId`
  - folder image -> `folderName`
- Import restores media to app storage first, then rewrites imported entities so `applyPackageImport()` reconnects everything correctly.

## UI changes
- Added vault chips to choose target/source vault.
- Added **Export ZIP** and **Import ZIP** actions.
- Existing package / JSON / CSV / VCF actions remain intact.

## Files changed
- `domain/vaults/.../VaultTransferRepository.kt`
- `app/.../TransferTaskCoordinator.kt`
- `app/.../ImportExportRoute.kt`
- `data/repository/.../VaultTransferRepositoryImpl.kt`
