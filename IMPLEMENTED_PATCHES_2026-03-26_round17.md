# Round 17 patches

- Replaced contact social icons using the uploaded SVG source set.
- Tightened icon bounds to reduce empty padding around logos.
- Kept transparent regions visually stable by rendering icons on a fixed white inner surface in contact details and manage-links fields.
- Added LinkedIn support to Manage links and contact-details icon actions.
- Replaced the in-app application logo with the new `app.svg` design.
- Propagated the new app logo through launcher resources, adaptive launcher foreground, alias icons, and existing in-app logo usages.
- Removed intermediate review artifacts from the release output, including `frame_sheet.png` and `frames/`.
