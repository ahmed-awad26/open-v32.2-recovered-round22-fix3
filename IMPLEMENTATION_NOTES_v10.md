Included in this iteration:
- Active vault opens as the home screen
- Drawer-based vault switching and settings entry
- No top back buttons in feature screens
- Top chip row for Search / Favorite / A-Z / Folders
- Floating rounded bottom bar for Search / Groups / Add / Dial
- Direct calling via ACTION_CALL when permission is granted, ACTION_DIAL fallback otherwise
- Contact edit/delete moved away from always-visible card actions; edit/delete are exposed in selection mode and inside contact details overflow menu
- Contact details overflow actions for share text / share file / edit / delete
- WhatsApp and Telegram quick actions in contact details
- Theme mode switching: Light / Dark / System
- Trash screen retained without a top back button
- Workspace screen supports create/delete tags and folders; assignment remains through contact editing

Not fully included in this build:
- Floating incoming-caller overlay for private contacts outside the system contacts provider
- Full Google Drive OAuth sync (existing project still uses staging/local backup handoff)
- True in-call UI replacing the system dialer/in-call screen

Reason:
These require deeper Android telecom / overlay role handling and stricter policy-aware implementation work.
