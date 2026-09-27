# OpenContacts V6 status

Implemented in this snapshot:
- Contact details with notes, reminders, timeline
- Expanded Room schema for notes/reminders/timeline/backup/import-export/tags/folders/cross refs
- Local encrypted backup + restore
- JSON / CSV / VCF export and import
- Phone contacts bridge import/export using ContactsContract
- Google Drive / OneDrive backup adapter layer using provider staging directories (auth wiring still pending)
- Reminder scheduling through WorkManager notifications

Still pending for a fuller production release:
- OAuth/authenticated Google Drive and Microsoft Graph upload flows
- Runtime permission request UX for contacts and notifications
- Rich tag/folder management UI (create/edit/delete)
- Full contacts-provider conflict merge UI
- Reminder notification deep links and editing
- Search indexing across expanded entities
