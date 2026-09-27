# Implemented patches - 2026-03-25

Applied in this bundle:

- Default launcher icon switched to green/emerald.
- Seed/default contact changed to `Test`.
- Blank contact save fallback changed from `test_Contact` to `Test`.
- Folder color palette expanded significantly.
- Tag color customization added in Workspace.
- Tag color persistence fixed so saving contacts no longer resets tag colors to default.
- Tag assignment behavior changed to exclusive replacement semantics:
  - assigning a tag to a contact removes that tag from other contacts first.
  - bulk tag assignment keeps the last selected contact as the owner of the tag.
- Contacts search and tags search now use a light debounce to reduce lag from immediate filtering on every keystroke.
- Local backup / restore now embeds contact photos and folder images as binary payloads in the backup instead of only storing raw paths/URIs.

Important caveats:

- This snapshot still uses a single primary phone number in the core data model.
- Full app-settings round-trip backup is not fully implemented in this patch set.
- Because the uploaded archive does not include a complete Gradle wrapper/distribution, a full Android assemble verification could not be executed in this environment.
