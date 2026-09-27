# Implemented patches — 2026-03-26 round14

## Contact details social icons
- Stabilized social icon presentation across dark and light modes.
- Added a neutral inner surface behind platform logos so raster logos do not visually wash out in dark theme.
- Kept the existing logo assets and click behavior intact.

## Import / Export / Backup progress UX
- Added transparent on-screen progress overlays for Import/Export and Backup/Restore screens.
- Overlay can be closed manually while the task keeps running in the background.
- Overlay now shows:
  - live percentage
  - current step label
  - status message
  - live/final import-export counters when available
    - imported
    - merged
    - skipped
    - failed
    - folders
    - tags
    - vaults
    - scanned
- Progress state now uses per-task session ids so closing one overlay does not suppress the next task.

## Background continuity
- Reused the existing singleton coordinator background scope.
- Import/export and backup tasks continue after leaving the page.
- UI now makes this explicit and lets the user dismiss the floating progress panel safely.

## Live stats support
- Extended `TransferProgressUpdate` with optional `ImportExportStats`.
- Coordinator now preserves and updates live stats snapshots during import flows.
- Added richer stats snapshots to package/json/zip/csv/vcf/phone flows and contact-merge phases.
