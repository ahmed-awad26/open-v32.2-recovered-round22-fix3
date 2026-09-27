Fixed compile blockers from CI log on top of round20-hotfix-localization1:

- ContactDetailsRoute.kt
  - Replaced incorrect `localizeText("Unknown number", arabicUi)` usage with composable-safe `localizedText("Unknown number")`.
- ReliableOutlinedTextField.kt
  - Added optional `leadingIcon` and `trailingIcon` parameters and passed them through to Material3 OutlinedTextField.
  - This fixes the `No parameter with name 'leadingIcon' found` error and the follow-up composable invocation errors in ContactDetailsRoute.

Notes:
- Room `fallbackToDestructiveMigration()` message remains a warning, not a build blocker.
- No intermediate review artifacts included.
