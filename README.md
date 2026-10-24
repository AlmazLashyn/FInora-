# Finora

**Finora** is a small, offline-first Android app for exploring everyday financial scenarios. It helps people estimate loan costs, plan savings and understand how rates and inflation affect money over time.

| Project | Details |
| --- | --- |
| Author / maintainer | Almaz |
| Project started | December 2025 |
| Current app version | 1.0.0 |
| Platform | Android 8.0 (API 26) and newer |
| Languages | English, Russian and Simplified Chinese |
| Implementation | Native Android UI and Java |

Finora is an educational planning tool. Its estimates are not bank offers or financial advice.

## Features

- **Loan:** estimate monthly payments, interest, fees and the repayment schedule. Supports fixed and declining payments.
- **Mortgage:** include the property price and down payment in a loan estimate.
- **Early repayment:** estimate how an extra monthly payment changes the payoff date and total cost.
- **Savings goal:** project compound growth with a starting balance and regular monthly contributions.
- **Deposit:** enter an opening deposit, monthly top-ups, term and nominal annual rate. An effective annual rate can be supplied as an override; if left blank, Finora derives it from the nominal rate using monthly compounding.
- **Inflation:** estimate the future purchasing power of an amount.
- **History and favorites:** successful calculations are saved locally. Star a result to find it later in the Favorites filter.
- **Currencies:** USD, EUR, KZT, RUB, CNY and JPY.
- **Charts:** lightweight in-app charts for balance and growth projections.

The app interface is available in English, Russian and Simplified Chinese. It works offline and stores calculation history on the device using SQLite. The Android manifest does not request network access.

## Build and run

Open the project folder in Android Studio, allow Gradle to sync, and run the `app` configuration on an emulator or Android device. Install Android SDK Platform 35 if Android Studio asks for it. Use JDK 17 or newer.

Build a debug APK from the terminal:

```sh
./gradlew :app:assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Run the existing unit-test suite or Android lint with:

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## How the estimates work

- Loan interest uses a monthly rate derived from the entered nominal annual rate (`annual rate / 12`). Payments and balances are rounded to the selected currency's minor unit; the last payment is adjusted to close the balance.
- Savings growth compounds monthly. The monthly contribution is added at the end of each month.
- A deposit uses the supplied effective annual rate when one is entered. Otherwise, the monthly rate is `nominal annual rate / 12`, and the effective annual rate is calculated as `(1 + monthly rate)^12 - 1`. Monthly top-ups are added at month end.
- Inflation compounds once per year for the selected whole-number term.

Actual bank products can use different accrual, capitalization, tax and fee rules. Finora does not model institution-specific terms unless represented by an input field.

## Project structure

```text
app/src/main/java/com/finora/app/
  MainActivity.java       Android screens, forms, navigation and localization
  data/ScenarioStore.java Local SQLite history and favorites
  domain/LoanEngine.java  Loan and repayment calculations
  domain/ProjectionEngine.java Savings, deposit and inflation calculations
app/src/main/res/          Localized text, themes and vector icons
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for calculation and storage details.

## License

No open-source license has been added yet. Until a license is selected, the source is visible on GitHub but reuse and redistribution are not granted by this repository.
