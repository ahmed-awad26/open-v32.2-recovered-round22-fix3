# Round 5 patches

## Contact details social shortcuts
- Reworked the social section in `ContactDetailsRoute`.
- Added branded direct shortcuts for WhatsApp and Telegram at the top of the social links area.
- Each shortcut uses the same deep-link/open behavior as the previous action flow.
- Kept `Manage links` so manual link editing still works.
- Extra non-WA/TG links still appear below as pills.

## Additional phone numbers editor
- Replaced the old multiline `Additional phones (one per line)` text box.
- Added structured extra phone entries where each additional number is stored independently.
- Every extra number now has its own:
  - phone number field
  - label field
  - remove action
- Added quick suggestion chips for labels: WhatsApp, Telegram, Home, Work.
- Applied the new editor in both:
  - contacts list full-screen editor
  - contact details editor

## Save / edit mapping
- `ContactEditorState` now stores `additionalPhoneEntries` instead of a raw multiline string.
- Edit flows now load existing additional numbers with their labels.
- Save flows now map each additional entry to `ContactPhoneNumber(value, type, label)`.
- Label-aware type inference added for Home / Work / Mobile, with custom fallback for other labels.

## Contact details number display
- Number chips inside contact details now show the label together with the number when available.
- Primary number area also shows its label when present.
