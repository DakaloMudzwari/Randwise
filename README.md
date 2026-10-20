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
| §7 POPIA & security | SQLCipher, BiometricPrompt gate, consent toggles, CSV/PDF export, purge, backups disabled |

All money is `Long` cents. Percentages and APRs are basis points. No floating point touches a financial total.

## Build & test

Requirements: JDK 17, Android SDK 35.

```bash
./gradlew :app:testDebugUnitTest   # JUnit 5 engine/parser tests
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and run the `app` configuration.

## AI coach gateway

The app works fully offline with a deterministic on-device coach. To enable Gemini:

```bash
cd backend/RandWise.Gateway
export GEMINI_API_KEY=your-google-ai-studio-key     # PowerShell: $env:GEMINI_API_KEY="…"
dotnet run                                          # listens on http://0.0.0.0:5080
```

In the app: **Settings → Privacy → AI interpretation** (opt-in). The default URL `http://10.0.2.2:5080`
reaches the host from the Android emulator. For a physical device or production, deploy the gateway
behind **HTTPS** and enter its URL. Cleartext is only permitted to `10.0.2.2`/`localhost`.

The gateway re-sanitises every payload, enforces a fixed system prompt and a JSON response schema,
rate-limits per IP and never logs request bodies.

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
and add a consent flow (Google UMP SDK) for personalised ads.

## Before shipping

- Replace AdMob test IDs; integrate the UMP consent SDK.
- Deploy the gateway over HTTPS with an auth mechanism (e.g. Play Integrity / App Check tokens).
- Room schema is version 1 with `exportSchema`; add migrations for any entity change.
- Add Compose UI tests and run a closed beta (FDS Phase 6).
