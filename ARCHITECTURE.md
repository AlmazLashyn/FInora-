# Finora architecture

## App layers

- `com.finora.app.MainActivity` builds the native Android screens, collects input, validates form values, localizes labels, shows results and draws small charts.
- `com.finora.app.domain.LoanEngine` contains the loan payment and amortization calculations. It is plain Java and does not depend on Android UI classes.
- `com.finora.app.domain.ProjectionEngine` contains savings, deposit and purchasing-power calculations.
- `com.finora.app.data.ScenarioStore` stores calculation inputs, result summaries, timestamps and favorite state in a private SQLite database on the device.
- `app/src/main/res/values*` contains the English, Russian and Simplified Chinese strings, themes, colors and vector assets.

## Navigation and data flow

Home presents the available calculators. A successful calculation creates a history entry automatically. The result's star toggles its favorite state; History can show all calculations or only favorites. Selecting a history item restores its inputs in the matching calculator form. Deleting a history entry or clearing history removes its local database row.

No calculation data is sent to a server. The app does not request network access.

## Calculation models

### Loans

The periodic interest rate is the nominal annual rate divided by twelve. Annuity payments use the standard amortization formula; differentiated payments use equal principal portions and interest on the outstanding balance. Early repayment adds the entered amount to each monthly payment, capped at the remaining amount due. Fees and monthly costs are included where the form provides those fields. Cash amounts are rounded to the selected currency scale, and the final payment is adjusted to close the balance.

### Savings goal

The nominal annual return is divided by twelve. Interest compounds monthly, and each regular contribution is added at the end of the month.

### Deposit

The user enters an opening balance, monthly top-up, term and nominal annual rate. An effective annual rate may also be entered. If supplied, the effective rate drives the projection and is converted to an equivalent monthly rate:

`monthly rate = (1 + effective annual rate)^(1/12) - 1`

If the effective rate is blank, the entered nominal rate is divided by twelve and the effective annual rate is derived as:

`effective annual rate = (1 + nominal annual rate / 12)^12 - 1`

Monthly top-ups are added at the end of each month. The calculated effective rate is an estimate based on monthly compounding; a bank may use different terms.

### Inflation

Purchasing power is estimated by dividing the entered amount by `(1 + annual inflation rate)^years`. The selected term is a whole number of years.

## Numeric and input limits

Financial amounts and amortization use `BigDecimal`. Projection formulas use a 28-digit HALF_EVEN intermediate context; displayed cash values use HALF_UP rounding. Loan terms are limited to 35 years. Projection periods are limited to 100 whole years. Rate and amount validation is enforced in the domain calculations.

## Persistence

`ScenarioStore` uses Android's built-in `SQLiteOpenHelper`; no external database dependency is required. The database has a versioned schema, including a favorite flag. A schema upgrade adds that flag to older history rows without deleting their stored scenarios.
