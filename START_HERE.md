# Start Here

1. Open `gradle/libs.versions.toml` and confirm versions if you want newer AndroidX artifacts.
2. Run the GitHub Actions workflow to produce a debug APK.
3. Start iterating in these areas first:
   - `app/` for navigation, unlock flow, and security settings
   - `feature/contacts/` for contact CRUD UX
   - `feature/vaults/` for vault lifecycle UX
   - `data/db/` and `data/repository/` for encrypted per-vault persistence
4. Next production tasks:
   - per-vault PIN / biometric policies
   - backup / restore
   - import / export
   - richer contact fields and tags model
