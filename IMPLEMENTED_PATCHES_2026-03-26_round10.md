# Round 10

- Rebuilt **Import & Export** screen so each format sits in one aligned row: VCF, CSV, JSON, phone contacts, Excel, internal package, and ZIP.
- Kept **Import strategy** and **Vault selection** at the top.
- Added **JSON import** in addition to JSON export.
- Added format-specific default folders under **Downloads/AW** and saved exports into dedicated subfolders:
  - `AW/VCF`
  - `AW/CSV`
  - `AW/JSON`
  - `AW/Excel`
  - `AW/InternalPackage`
  - `AW/ZIP`
- Picked import files are also staged into matching AW subfolders while preserving the existing internal staging path for compatibility.
- Updated repository lookup to search the matching AW format folder when importing latest files.
- Changed drawer layout to a **scrolling upper content area** with a **fixed bottom bar**.
- Added **Settings** button in the fixed drawer footer.
- Added **Dark / Light toggle** beside Settings in the fixed drawer footer.
- Removed redundant top-right Settings button from Contacts header so drawer footer becomes the stable settings entry point.
