# Contact Manager v0.12.2a (TEST)

Purpose:
- investigate and fix startup crashes observed in v0.12.2,
- keep v0.12.2 as pre-release,
- provide a more stable test build.

Fixes and hardening:
- replaced risky drawable setup in header icon rendering,
- replaced contact card root from MaterialCardView to a plain rounded/stroked layout,
- added structured app event logger with crash capture,
- added safe startup fallback screen: if main layout init fails, app stays open and offers retry,
- added parse-failure diagnostics for corrupted saved contacts.

Crash/event logs:
- on each app launch, a new text log file is created in:
  `Android/data/com.example.contactmanagerdemo/files/Documents/ContactManagerLogs/`
- logs include key lifecycle/data actions and uncaught exception stack traces.

APK:
- `ContactManager-v0.12.2a-debug.apk`
