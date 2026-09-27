# Round 22 — 2026-09-25 (Claude, recovered branch)

## Context
Ahmed found and re-uploaded `open-v32_2-main-updated.zip`, which turned out to be the "lost" branch
that already contains Folders Panel (`FOLDER_PANEL_PLACEMENT`/`FOLDER_PANEL_MODE`), the full SMS
composer (`SmsComposerRoute.kt` + `SmsRepository.kt`), `LockScreenCallActivity.kt`, and
`MiniCallViewService.kt` — independently built up to round21-hotfix2 (2026-03-26), separate from the
`open-v32.2-main.zip` baseline used for the previous round 22. **This branch now supersedes that one.**
Any work done on the other baseline (its own round 22 — Folders Panel rebuilt from scratch) should be
treated as superseded/discarded in favor of this branch's own, more complete implementation.

## Verified as real and working (not fake/stub)
- `SmsComposerRoute.kt` (434 lines) + `SmsRepository.kt`: manual number entry or contact picker,
  multipart SMS via `divideMessage`, real sent/delivered `PendingIntent` tracking, dual-SIM selection,
  draft restoration via `rememberSaveable`, and an honest Flash/Class‑0 SMS disclaimer (no hidden-API
  fakery — clearly states it isn't supported through standard Android APIs and falls back to normal SMS).
- `LockScreenCallActivity.kt` (487 lines) and `MiniCallViewService.kt` (301 lines): substantial, real
  implementations, properly registered in the manifest.
- Folder panel settings (`folderPanelPlacement` / `folderPanelMode`) fully wired through
  `AppLockRepository` → UI, independent implementation of the same feature I'd started rebuilding on the
  other baseline.

## Bugs found and fixed this round
1. **Compile-breaking**: `LockScreenCallActivity.kt` had two import statements merged onto one line
   (`...fillMaxWidthimport androidx...padding`) — would not compile. Fixed.
1b. **Compile-breaking (found after a real CI run failed on `:app:kspDebugKotlin`)**: the same file's
   `LockScreenCallScreen` composable uses `ModalBottomSheet` — an experimental Material3 API — without
   `@OptIn(ExperimentalMaterial3Api::class)`. Every other file in the app that uses an experimental M3
   API (`AppearanceRoute.kt`, `SmsComposerRoute.kt`, `SearchRoute.kt`, etc.) has this annotation; this
   was the one file missing it. Added `@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)`
   on `LockScreenCallScreen`. Verified repo-wide afterward that every file using `ModalBottomSheet` /
   other flagged experimental APIs now has at least one `@OptIn` in the file, and re-checked brace/paren
   balance across every `.kt` file in the repo (one apparent imbalance in
   `VaultTransferRepositoryImpl.kt` was a false positive from a `"{"` string literal, not a real bug).
1c. **Compile-breaking (found after Ahmed's second CI run — exact compiler error this time)**:
   `NotificationsIncomingCallsRoute.kt:373` — a `Text(...)` string literal had unescaped nested quotes:
   `"...the "Display over other apps" permission..."`. The middle `"Display over other apps"` broke out
   of the outer string, leaving `Display over other apps` as bare (invalid) tokens — exactly
   `e: ...:373:90 Expecting ','`. This is the exact same bug class Claude's memory recalled from an
   earlier, unrelated session on a different branch — same mistake, different occurrence. Fixed by
   escaping the inner quotes (`\"Display over other apps\"`). Ran two repo-wide scans afterward
   (odd-quote-count per line, and a pattern match for `word "Quoted Phrase" word` inside string
   contexts) — no other occurrences of this bug found anywhere else in the codebase.
2. **Real crash risk**: `MiniCallViewService.show()` called `startForegroundService()` but the service
   never called `startForeground()` — throws `ForegroundServiceDidNotStartInTimeException` on Android
   8+ a few seconds after the mini call view is shown. Changed to plain `startService()`: this service
   only manages a short-lived overlay while a call is active (the InCallService binding already keeps
   the process alive), so foreground-service status isn't needed and a real notification-backed
   `startForeground()` would just duplicate the existing ongoing-call notification.
3. **ANR risk (systemic, same class as the other baseline's audit)**: `runBlocking` reading DataStore
   settings synchronously in 5 places, several on the main thread:
   `IncomingCallActionReceiver.onReceive` (Answer/Open notification actions), `BootCompletedReceiver`,
   `FloatingIncomingCallService.showOverlay`, `MiniCallViewService.showMini`,
   `ActiveCallControlsActivity`'s call-recording start (`CallAudioRecorder.start()`).
   **Fixed at the root instead of patching each call site individually**: added a synchronous,
   non-blocking settings snapshot to `AppLockRepository` —
   `val currentSettings: AppLockSettings` backed by `settings.stateIn(scope, SharingStarted.Eagerly, ...)`.
   `postOngoingCallNotification` and `presentIncomingCallExperience` now read `currentSettings` instead
   of blocking with `runBlocking`. The two receivers were also wrapped in `goAsync()` + a background
   coroutine (defense in depth, and needed for their other suspend work), and
   `CallAudioRecorder.start()` was converted to a real `suspend fun`, called from the composable's
   existing `rememberCoroutineScope()` instead of blocking the click handler.
   **Zero `runBlocking(` calls remain anywhere in the codebase** (verified by repo-wide grep).
4. **Unused dangerous permission**: `RECEIVE_SMS` was declared but nothing in the codebase handles
   incoming SMS (only sending is implemented). Removed — only `SEND_SMS` is actually used.
5. **Same housekeeping as the other baseline**: `gradlew`/`gradlew.bat` were placeholder stubs with no
   `gradle/wrapper/gradle-wrapper.jar` or `.properties` — replaced with the real Gradle 8.9 wrapper
   (matches this branch's AGP 8.6.1 too). Removed the dead duplicate `workflows/build-apk.yml` outside
   `.github/`.

## Not yet done in this branch (carried over from the other baseline's audit, still open)
- Room migration gap on `VaultDatabase`/`VaultRegistryDatabase` if it exists in this branch's DB
  layer — not re-checked yet this round, needs the same review as before.
- `SearchRoute` reachability — not re-checked in this branch yet.
- Everything past Folders Panel/SMS/lock-screen/mini-view in Ahmed's original backlog (smart call
  features, autodialer/call scheduler, call merge, Google Contacts sync, app rename, versioning scheme,
  CI/CD update-in-place, replacing the system contacts app, etc.) — none of it is in this branch either.

### Files touched this round
- `app/src/main/java/com/opencontacts/app/LockScreenCallActivity.kt` (import fix)
- `app/src/main/java/com/opencontacts/app/MiniCallViewService.kt` (foreground-service crash fix,
  runBlocking removal)
- `app/src/main/java/com/opencontacts/app/FloatingIncomingCallService.kt` (runBlocking removal)
- `app/src/main/java/com/opencontacts/app/IncomingCallSupport.kt` (goAsync wrap, runBlocking removal)
- `app/src/main/java/com/opencontacts/app/BootCompletedReceiver.kt` (goAsync wrap, runBlocking removal)
- `app/src/main/java/com/opencontacts/app/ActiveCallControlsActivity.kt` (suspend recorder.start())
- `app/src/main/java/com/opencontacts/app/DefaultDialerInCallService.kt` (runBlocking removal)
- `core/crypto/src/main/java/com/opencontacts/core/crypto/AppLockRepository.kt` (new `currentSettings`
  synchronous cache)
- `app/src/main/AndroidManifest.xml` (removed unused RECEIVE_SMS)
- `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `gradlew`,
  `gradlew.bat` (real wrapper)
- Removed: `workflows/build-apk.yml` (dead duplicate)

### Not touched
No changes to Room schemas, navigation graph routes, crypto/vault code, or any UI beyond the specific
fixes above.
