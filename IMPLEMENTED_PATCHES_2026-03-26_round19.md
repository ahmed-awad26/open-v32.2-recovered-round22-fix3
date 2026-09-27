Round 19 changes

- Added ReliableOutlinedTextField and replaced editor/form text boxes in contact editor, details, vaults, import/export, unlock, security, workspace and phone editors to reduce the intermittent first-focus / first-typing issue.
- Kept the main home search field unchanged.
- Added ContactRepository.warmUpVault(vaultId) and call it when the app picks the default vault and when switching vaults, so SQLCipher/Room are opened before the contacts screen requests the list.
- Changed key StateFlows in AppViewModel and ContactsViewModel from WhileSubscribed to Eagerly to keep contact data warm and reduce visible reload delay when opening the app or returning to Contacts.
