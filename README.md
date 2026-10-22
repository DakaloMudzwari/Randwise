# RandWise

Personal finance and behavioural spending app for Android (Kotlin, Jetpack Compose), built from the
*RandWise Functional Design Specification*. Local-first, encrypted, integer-cent maths, with an optional
Gemini-powered coach behind an ASP.NET Core gateway.

## Project layout

```
app/                                Android app (single activity, MVVM)
  src/main/java/com/randwise/app/
    core/Money.kt                   Cents formatting/parsing, integer rounding, basis points
    data/model/Entities.kt          Room entities (UserProfile, Account, Transaction, Budget, RecurringBill, Debt, Goal, …)
    data/db/                        DAOs + SQLCipher-encrypted RandWiseDatabase
    data/FinanceRepository.kt       Ledger, transfers, import staging/commit, budgets, export, purge
    domain/                         Pure Kotlin engines (no Android deps) — see table below
    importer/                       CSV / PDF text / OCR extraction, parsers, dedup fingerprints, categoriser
    ai/                             Anonymised context builder, gateway client, offline LocalCoach
    security/DatabaseKeyManager.kt  Keystore-wrapped SQLCipher passphrase
    work/BillReminderWorker.kt      Daily WorkManager reminders
    ui/                             Compose screens, theme, charts, ViewModels
  src/test/                         JUnit 5 tests for the maths engines and parsers
backend/RandWise.Gateway/           ASP.NET Core minimal API → Google Gemini
```

## FDS coverage

| FDS | Where |
|---|---|
| F01 Profile setup | `ui/screens/OnboardingScreen.kt`, `Settings → Financial profile` |
| F02 Transaction engine (transfers net-zero, card payments not double-counted, refunds offset) | `domain/Ledger.kt`, `FinanceRepository.addTransfer` |
| F03 Statement parser (CSV, text PDF via PDFBox, scanned PDF via ML Kit OCR, balance validation, dedup review) | `importer/*`, `ui/screens/ImportScreen.kt` |
| F04 Smart budget (spent / committed / remaining per pay cycle, buckets, warnings) | `domain/engine/CoreEngines.kt` (`BudgetEngine`), `BudgetScreen.kt` |
| F05 Behavioural analytics (payday spikes, subscription creep, fee traps, 30-day volatility, lifestyle creep) | `domain/engine/Behavior.kt` |
| F06 Affordability checker | `domain/engine/Forecasting.kt` (`AffordabilityEngine`), Insights → What-if |
| F07 Cash-flow forecast (7/14/30/90 days) | `CashFlowForecaster`, Insights → Trends |
| F08 Debt snowball vs avalanche, emergency coverage | `domain/engine/Planning.kt`, Budget tab |
| Eq. 1 Safe-to-spend | `SafeToSpendEngine.compute` |
| Eq. 2 Velocity projection (linear + 7-day weighted) | `VelocityEngine` |
| §5 Hybrid AI (facts on-device, Gemini only explains) | `ai/ContextBuilder.kt`, `backend/` |
| §7 POPIA & security | SQLCipher, app PIN/password + biometrics, consent log, privacy notice, CSV/PDF export, full erasure, backups disabled — see below |
| Pay & bills | Monthly, fortnightly or weekly pay; weekly, monthly or yearly bills (`domain/PayCycle.kt`); paydays roll back past weekends and SA public holidays (`domain/SaPublicHolidays.kt`) |
| Debt minimums | Fixed instalments or % of balance with a floor, as SA cards and store accounts work (`Debt.minimumFor`) |
| Learning categories | Recategorising offers to fix the same merchant elsewhere and remember it for imports (`domain/CategoryLearning.kt`) |
| Alerts | Bills due in 2 days, 80% / over-budget alerts once per cycle (`domain/engine/BudgetAlerts.kt`, `work/BillReminderWorker.kt`) |
| Money check-up | Home → three months of spending by category and shop, trends, AI insights and suggestions, and a suggested budget you tick, edit and accept (`domain/engine/SpendingReview.kt`, `ai/LocalCheckup.kt`, `ui/screens/CheckupScreen.kt`, gateway `/api/checkup`) |
| Own budget lines | Budget → + → *New category* with its own limit |

All money is `Long` cents. Percentages and APRs are basis points. No floating point touches a financial total.

## Build & test

Requirements: JDK 17, Android SDK 35.

```bash
./gradlew :app:testDebugUnitTest   # JUnit 5: engines, parsers, pay/bill frequencies, password hashing
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk

# Instrumented tests (Room migration, lock screen) on an emulator, without touching your normal install:
./gradlew :app:connectedDebugAndroidTest -PrwPreview -PrwAbi=x86_64
```

Build flags: `-PrwPreview` installs a side-by-side `com.randwise.app.preview` with its own data;
`-PrwAbi=x86_64` packages a single CPU architecture (53 MB instead of 97 MB) for space-constrained emulators.

Or open the folder in Android Studio and run the `app` configuration.

**Quality gates:** `./gradlew :app:lintDebug` must report zero errors (policy in `app/lint.xml`); CI runs unit tests,
lint, the debug build, and the gateway build + tests.

**Size:** OCR comes from Google Play services (unbundled ML Kit), and release builds shrink code and resources —
the release APK is ~31 MB (down from 75 MB) and Play delivers ~16 MB to an arm64 phone from the App Bundle
(`./gradlew bundleRelease`). Devices without Google Play services can't read scanned PDFs; CSV and text PDFs still work.

**Database migrations:** the schema is versioned with exported JSON in `app/schemas`. Every entity change needs
a new version, a migration (auto or manual) and a case in `MigrationTest` — never destructive fallback.

## Security & privacy

- **App password** — users can set a 6–10 digit PIN or an 8+ character password (Vault, or the last onboarding step).
  Only a salted PBKDF2-HMAC-SHA256 hash (120k iterations) is stored. Five free attempts, then an escalating lockout
  (30 s → 15 min) that survives restarts. Fingerprint/face is an optional shortcut. A forgotten password can only be
  reset by erasing the app's data — by design there is no recovery path.
- **Encryption at rest** — SQLCipher with a random 256-bit key wrapped by an Android Keystore AES-GCM key.
- **Screen privacy** — `FLAG_SECURE` (on by default) blocks screenshots and the recent-apps preview; reminder
  notifications show no amounts on the lock screen.
- **Erasure** — "Erase all local data" deletes the database file, destroys the Keystore key, clears the password,
  settings and caches, then restarts the app.
- **POPIA** — versioned privacy notice in-app (`data/Privacy.kt`), timestamped consent history, explicit consent
  before statements are read and before anonymised totals go offshore to Gemini.
- **Ads** — Google UMP consent runs before the Ads SDK initialises or any banner loads.

## AI coach gateway

The app works fully offline with a deterministic on-device coach. To enable Gemini:

```bash
cd backend/RandWise.Gateway
export GEMINI_API_KEY=your-google-ai-studio-key     # PowerShell: $env:GEMINI_API_KEY="…"
dotnet run                                          # listens on http://0.0.0.0:5080
```

In the app: **Settings → Privacy → AI interpretation** (opt-in). The default URL `http://10.0.2.2:5080`
reaches the host from the Android emulator. For a physical device or production, deploy the gateway
behind **HTTPS** — debug builds can enter its URL in Settings, release builds take it from `randwise.gatewayUrl`
(see *Release build*). Cleartext is only permitted to `10.0.2.2`/`localhost`.

The gateway re-sanitises every payload, enforces a fixed system prompt and a JSON response schema,
rate-limits per IP and never logs request bodies.

**Model:** `gemini-3.5-flash` by default (`Gemini:Model` in `appsettings.json`, or `Gemini__Model=…` as an environment
variable). Google retires models for new keys over time — `gemini-2.5-flash` already returns 404 for new accounts — so
check the [model list](https://ai.google.dev/gemini-api/docs/models) when a call starts failing. A "high demand" 503/429
is retried once automatically.

**Money check-up (`/api/checkup`):** the phone computes a three-month profile (median month per category, top shops
by first word only, fees) and a baseline budget (`BudgetPlanner`: essentials at their typical month, wants 10% under,
trimmed to fit income after savings). Gemini explains it and may tailor the limits; the app only accepts limits for
known categories, within 50–120% of the typical month and within income. Off, or unreachable, the same check-up is
built on the phone (`LocalCheckup`). The latest check-up is kept in the encrypted database.

**Answer grounding:** every rand amount in Gemini's answer must match a figure in the facts (or the user's question)
to the nearest rand (`AnswerGrounding.cs`). An invented amount triggers one corrective retry; if the model still can't
stay grounded the answer is returned with a visible "couldn't be verified" warning. Tests: `dotnet test backend/RandWise.Gateway.Tests`.
Coach conversations are saved in the encrypted database (latest 200 turns) and can be cleared from the Coach tab.

**Client key:** set `RANDWISE_CLIENT_KEY` on the gateway and the same value as `randwise.gatewayClientKey=…` in your
git-ignored `local.properties`; calls without it get `401`. The key ships inside the APK, so treat it as a speed bump —
add Play Integrity or Firebase App Check before scaling. A `Dockerfile` is included for HTTPS hosting
(Cloud Run, Azure Container Apps, or behind a reverse proxy).

## Design — "Midnight"

Dark-first: deep navy canvas with slowly drifting emerald/teal aurora light, glass cards and white type.
Space Grotesk (numbers, headings) + Manrope (body), bundled in `res/font` (SIL OFL).

- **Home** — 56-tick safe-to-spend gauge with count-up figure, a stacked bar visualising Equation 1
  (safe / bills / savings / buffer), quick actions, and a *payday runway* timeline with bills pinned to their days.
- **Coach** — animated gradient orb, chat bubbles, typing indicator; Trends with gradient area charts,
  animated donut and a behaviour scorecard; *What-if* with its own keypad and an animated verdict card.
- **Budget** — progress-ring tiles, calendar day badges for debit orders, emergency-fund hero, and
  snowball vs avalanche cards that crown the cheaper strategy.
- **Accounts** — accounts rendered as bank cards; dashed statement drop zone.
- Floating pill navigation; press-scale springs and staggered entrance motion throughout.

Shared building blocks live in `ui/components` (`Common.kt`, `Charts.kt`, `FloatingNavBar.kt`) and tokens in `ui/theme/Theme.kt`.

**Demo mode:** on the welcome screen, *Explore with demo data* loads a realistic South African household
(`data/DemoData.kt`) so every screen has something to show.

**Side-by-side preview:** `./gradlew installDebug -PrwPreview` installs `com.randwise.app.preview`
with fresh data, leaving your normal debug install untouched.

## Ads

AdMob banners use a flat neutral grey container labelled “Advertisement” — a colour used nowhere else in
the UI — placed only at the end of Home and Insights → Trends, away from navigation and never inside
financial cards, onboarding, import or settings.

The build uses Google's **test** AdMob IDs (`app/build.gradle.kts`). Before release, replace them with your own
and configure your consent message in the AdMob console (the UMP flow is already wired in).

## Release build

`./gradlew bundleRelease` refuses to run until these are set in `local.properties` (git-ignored) or as `-P` flags:

```properties
randwise.signing.storeFile=../keys/randwise-upload.jks   # Play App Signing upload key; back it up
randwise.signing.storePassword=…
randwise.signing.keyAlias=upload
randwise.signing.keyPassword=…
randwise.admobAppId=ca-app-pub-…~…
randwise.admobBannerId=ca-app-pub-…/…
randwise.gatewayUrl=https://your-gateway.example.com     # release builds always use this; no URL field in Settings
randwise.gatewayClientKey=…                              # same value as RANDWISE_CLIENT_KEY on the gateway
```

Debug builds always use AdMob test IDs and the emulator gateway (editable in Settings).

Gateway in production: set `RANDWISE_TRUST_PROXY=true` behind Cloud Run / Container Apps / a reverse proxy so the
per-IP limit sees real client addresses, and `RANDWISE_DAILY_REQUEST_CAP` (default 5000) to bound the Gemini bill.
Use a **paid** Gemini API tier — free-tier prompts may be used by Google to improve its products.

## Before shipping

- Set up the UMP consent message in AdMob.
- Add Play Integrity / App Check on top of the client key.
- Have the privacy notice reviewed (Information Officer registration, POPIA s72 cross-border wording, FAIS boundary).
- Translations (Afrikaans, isiZulu…) — move UI strings to `strings.xml` and engage translators.
- Crash reporting (Crashlytics or Sentry) with PII scrubbing — needs your project/DSN.
- Ledger P0 items: DB-side balance totals, statement reconciliation, transfer-pair matching, encrypted backup.
- Closed beta (FDS Phase 6).
