# Round 13 changes

- Fixed backup restore flow to support both:
  - new portable `.ocbak` backups stored as wrapped JSON payloads
  - older vault-encrypted backups with fallback decryption using the vault parsed from the backup filename when needed
- Reduced restore failures caused by decrypting older backups with the wrong active vault key.
- Moved **Backup & Restore** under **Import & Export** by adding a dedicated entry at the end of the Import & Export screen.
- Added a dedicated sub-screen route: `settings/importexport/backup`.
- Removed the standalone **Backup & Export** entry from the main Settings home list.
- Kept the older `settings/backup` route for compatibility, while routing UI entry points toward the new backup location.
- Renamed the contacts tab label from **Call log** to **Recents**.
