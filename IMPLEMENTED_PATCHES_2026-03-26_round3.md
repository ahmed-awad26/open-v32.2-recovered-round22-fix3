# Round 3 adjustments — 2026-03-26

## User correction applied
The previous tag behavior was too restrictive. It has now been corrected so that:
- one tag can belong to multiple contacts
- adding a tag to a contact does **not** remove it from other contacts
- bulk tag assignment adds the tag to all selected contacts
- duplicate tag entries on the same contact are still prevented

## Tag logic files updated
- `feature/contacts/src/main/java/com/opencontacts/feature/contacts/ContactsViewModel.kt`
  - `assignTagToMany()` now adds the tag to every selected contact instead of enforcing one-contact ownership.
- `feature/contacts/src/main/java/com/opencontacts/feature/contacts/ContactDetailsRoute.kt`
  - `addTag()` now adds the tag only to the current contact and does not strip it from other contacts.
- `app/src/main/java/com/opencontacts/app/WorkspaceRoute.kt`
  - `assignTagToContacts()` now behaves as additive multi-contact tagging.

## Trash improvements
Added a clear manual way to empty the trash with one press.

### Implemented
- Added a visible **Empty trash** button inside the Trash screen.
- Added confirmation dialog before permanent emptying.
- Added confirmation dialog for single-contact permanent deletion.
- Improved Trash screen layout to be more dashboard-like and readable.
- Reused existing repository purge path via `purgeDeletedOlderThan(vaultId, Long.MAX_VALUE)` to permanently clear all deleted contacts.

## Trash file updated
- `app/src/main/java/com/opencontacts/app/TrashRoute.kt`

## Resulting behavior
### Tags
- Tag `Work` can now be attached to Ahmed, Sara, and Mona at the same time.
- Adding `Work` to a new contact does not remove it from the others.
- Re-adding the same tag to the same contact will not create duplicates.

### Trash
- User can go to **Settings > Trash** and press **Empty trash**.
- A confirmation dialog appears.
- Confirming permanently deletes all contacts currently in trash.
