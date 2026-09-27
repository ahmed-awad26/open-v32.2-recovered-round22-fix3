# Claude Merge Notes

The uploaded `files.zip` was reviewed and selectively merged into this project.

Merged/adapted into the current architecture:
- `VcfImportExport.kt` → adapted into `data/repository/.../VcfCsvCodec.kt`
- `BackupSerializer.kt` → adapted conceptually into `data/repository/.../BackupFileCodec.kt`
- `TagUseCases.kt` → adapted into `domain/contacts/.../TagUseCases.kt`

Reviewed but **not** merged verbatim because they target a different package tree / domain model / navigation system:
- `AppNavigation.kt`
- `BackupRepositoryImpl.kt`
- `BackupScreen.kt`
- `ImportExportScreen.kt`
- `VcfHandlerTest.kt`

Why not merged verbatim:
- They use `com.aw.opencontacts...` packages and a different clean-architecture surface.
- They assume domain models (`Contact`, `Vault`, `BackupEntry`, etc.) that do not exist in this starter.
- Direct copy would create build conflicts and break the current Hilt/Room/navigation setup.

Instead, the useful logic was ported into the current `com.opencontacts...` structure.
