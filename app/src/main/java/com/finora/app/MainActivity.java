package com.finora.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.finora.app.data.ScenarioStore;
import com.finora.app.domain.LoanEngine;
import com.finora.app.domain.ProjectionEngine;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Finora's native Java interface. Financial formulas live in the pure domain package. */
public class MainActivity extends Activity {
    private static final int PAGE_HOME = 0, PAGE_HISTORY = 1, PAGE_FORM = 2;
    private static final String LOAN = "loan", MORTGAGE = "mortgage", EARLY = "early", SAVINGS = "savings", DEPOSIT = "deposit", INFLATION = "inflation";
    private int page = PAGE_HOME;
    private int selectedTab = PAGE_HOME;
    private int returnPage = PAGE_HOME;
    private String calculator = LOAN;
    private LinearLayout root, pageContent, navBar;
    private LinearLayout formBody;
    private LinearLayout formInputs;
    private ScrollView formScroll;
    private final java.util.Map<String, EditText> fields = new java.util.LinkedHashMap<>();
    private Spinner currencySpinner, paymentSpinner;
    private ScenarioStore scenarioStore;
    private String resultSummary = "";
    private String inputsSnapshot = "";
    private String resultCurrency = "USD";
    private String pendingScenarioInputs;
    private LinearLayout currentResultCard;
    private TextView resultFavoriteButton;
    private long currentScenarioId = -1;
    private boolean currentScenarioFavorite;
    private boolean showFavoritesOnly;
    private TextView calculationError;
    private List<LoanEngine.PaymentRow> currentSchedule = new ArrayList<>();
    private int scheduleVisibleCount;

    @Override protected void attachBaseContext(Context base) {
        String language = base.getSharedPreferences("finora", MODE_PRIVATE).getString("language", "en");
        Locale locale = localeFor(language);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        scenarioStore = new ScenarioStore(this);
        getWindow().setStatusBarColor(color(R.color.canvas));
        getWindow().setNavigationBarColor(color(R.color.surface));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(R.color.canvas));
        pageContent = new LinearLayout(this);
        pageContent.setOrientation(LinearLayout.VERTICAL);
        pageContent.setBackgroundColor(color(R.color.canvas));
        root.addView(pageContent, new LinearLayout.LayoutParams(-1, 0, 1));
        navBar = new LinearLayout(this);
        navBar.setGravity(Gravity.CENTER);
        navBar.setBackgroundColor(color(R.color.surface));
        root.addView(navBar, new LinearLayout.LayoutParams(-1, dp(66)));
        setContentView(root);
        showPage(PAGE_HOME);
    }

    @Override public void onBackPressed() {
        if (page == PAGE_FORM) showPage(returnPage);
        else if (page != PAGE_HOME) showPage(PAGE_HOME);
        else super.onBackPressed();
    }

    private void showPage(int destination) {
        page = destination;
        if (destination != PAGE_FORM) selectedTab = destination;
        pageContent.removeAllViews();
        if (destination == PAGE_FORM) buildCalculatorForm();
        else if (destination == PAGE_HOME) buildHome();
        else buildHistory();
        buildNavigation();
    }

    private ScrollView scrollPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        pageContent.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(18), dp(20), dp(26));
        scroll.addView(body);
        return scroll;
    }

    private LinearLayout bodyOf(ScrollView scroll) { return (LinearLayout) scroll.getChildAt(0); }

    private void buildHome() {
        LinearLayout body = bodyOf(scrollPage());
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_finora_mark);
        top.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout brand = new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(10), 0, 0, 0);
        brand.addView(text("Finora", 18, R.color.ink, true));
        brand.addView(text(getString(R.string.tagline), 11, R.color.muted, false));
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView lang = text(languageShort(), 13, R.color.brand_navy, true);
        lang.setContentDescription(getString(R.string.language));
        lang.setGravity(Gravity.CENTER); lang.setBackground(round(R.color.surface, R.color.line, 22));
        lang.setPadding(dp(14), dp(9), dp(14), dp(9)); lang.setOnClickListener(v -> showLanguageMenu(v));
        top.addView(lang);
        body.addView(top);
        addSpace(body, 22);
        body.addView(text(getString(R.string.greeting), 24, R.color.ink, true));
        addSpace(body, 6);
        body.addView(text(getString(R.string.home_subtitle), 13, R.color.muted, false));
        addSpace(body, 22);
        body.addView(text(getString(R.string.all_tools), 18, R.color.ink, true));
        addToolGridRow(body,
                new Tool(R.drawable.ic_calculator, getString(R.string.loan), getString(R.string.loan_desc), LOAN),
                new Tool(R.drawable.ic_home, getString(R.string.mortgage), getString(R.string.mortgage_desc), MORTGAGE));
        addToolGridRow(body,
                new Tool(R.drawable.ic_early, getString(R.string.early_repayment), getString(R.string.early_desc), EARLY),
                new Tool(R.drawable.ic_savings, getString(R.string.savings), getString(R.string.savings_desc), SAVINGS));
        addToolGridRow(body,
                new Tool(R.drawable.ic_deposit, getString(R.string.deposit), getString(R.string.deposit_desc), DEPOSIT),
                new Tool(R.drawable.ic_inflation, getString(R.string.inflation), getString(R.string.inflation_desc), INFLATION));
        addSpace(body, 12);
        body.addView(text(getString(R.string.finance_note), 11, R.color.muted, false));
    }

    private static final class Tool {
        final int icon;
        final String title;
        final String description;
        final String type;
        Tool(int icon, String title, String description, String type) {
            this.icon = icon; this.title = title; this.description = description; this.type = type;
        }
    }

    private void addToolGridRow(LinearLayout body, Tool first, Tool second) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        if (second != null) {
            row.addView(toolTile(first), new LinearLayout.LayoutParams(0, -2, 1));
            View gap = new View(this);
            row.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));
            row.addView(toolTile(second), new LinearLayout.LayoutParams(0, -2, 1));
        } else {
            View leftSpace = new View(this);
            row.addView(leftSpace, new LinearLayout.LayoutParams(0, 1, 0.5f));
            row.addView(toolTile(first), new LinearLayout.LayoutParams(0, -2, 1));
            View rightSpace = new View(this);
            row.addView(rightSpace, new LinearLayout.LayoutParams(0, 1, 0.5f));
        }
        body.addView(row, spacedParams(10));
    }

    private LinearLayout toolTile(Tool tool) {
        LinearLayout tile = card();
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.TOP);
        tile.setPadding(dp(13), dp(14), dp(13), dp(14));
        tile.setMinimumHeight(dp(154));
        ImageView image = icon(tool.icon);
        tile.addView(image, new LinearLayout.LayoutParams(dp(52), dp(52)));
        addSpace(tile, 11);
        tile.addView(text(tool.title, 15, R.color.ink, true));
        addSpace(tile, 4);
        tile.addView(text(tool.description, 11, R.color.muted, false));
        tile.setOnClickListener(v -> openCalculator(tool.type));
        return tile;
    }

    private void buildCalculatorForm() {
        fields.clear(); currentSchedule = new ArrayList<>(); resultSummary = ""; scheduleVisibleCount = 0;
        currencySpinner = null; paymentSpinner = null; currentResultCard = null; resultFavoriteButton = null;
        currentScenarioId = -1; currentScenarioFavorite = false;
        ScrollView scroll = scrollPage();
        formScroll = scroll;
        formBody = bodyOf(scroll);
        TextView back = text("‹  " + getString(R.string.back), 14, R.color.brand_green, true);
        back.setPadding(0, 0, 0, dp(13)); back.setOnClickListener(v -> showPage(returnPage)); formBody.addView(back);
        String title = calculatorTitle(calculator);
        pageHeader(formBody, title, calculatorSubtitle(calculator));
        addLabel(formBody, getString(R.string.enter_values));
        LinearLayout inputs = card(); inputs.setOrientation(LinearLayout.VERTICAL); inputs.setPadding(dp(16), dp(16), dp(16), dp(16));
        formInputs = inputs;
        formBody.addView(inputs, spacedParams(14));

        if (calculator.equals(LOAN) || calculator.equals(MORTGAGE) || calculator.equals(EARLY)) {
            if (calculator.equals(MORTGAGE)) addField(inputs, "homePrice", getString(R.string.home_price), "250000", true);
            addField(inputs, "amount", getString(calculator.equals(MORTGAGE) ? R.string.down_payment : R.string.amount), calculator.equals(MORTGAGE) ? "50000" : "25000", true);
            addCurrencyField(inputs);
            addField(inputs, "rate", getString(R.string.annual_rate), calculator.equals(MORTGAGE) ? "6.5" : "8.9", true);
            addField(inputs, "years", getString(R.string.term_years), calculator.equals(MORTGAGE) ? "20" : "5", false);
            if (!calculator.equals(EARLY)) addPaymentType(inputs);
            if (calculator.equals(EARLY)) addField(inputs, "extra", getString(R.string.extra_monthly), "100", true);
            else {
                addField(inputs, "fees", getString(R.string.fees), "0", true);
                addField(inputs, "monthlyCosts", getString(R.string.monthly_costs), "0", true);
            }
        } else if (calculator.equals(SAVINGS)) {
            addField(inputs, "initial", getString(R.string.initial_balance), "10000", true);
            addField(inputs, "monthly", getString(R.string.monthly_contribution), "300", true);
            addField(inputs, "rate", getString(R.string.annual_return), "5", true);
            addField(inputs, "years", getString(R.string.years), "10", false);
            addCurrencyField(inputs);
        } else if (calculator.equals(DEPOSIT)) {
            addField(inputs, "initial", getString(R.string.deposit_initial), "10000", true);
            addField(inputs, "monthly", getString(R.string.monthly_contribution), "300", true);
            addField(inputs, "rate", getString(R.string.deposit_nominal_rate), "15", true);
            addField(inputs, "effectiveRate", getString(R.string.deposit_effective_rate), "", true);
            inputs.addView(text(getString(R.string.deposit_effective_hint), 11, R.color.muted, false), spacedParams(5));
            addField(inputs, "years", getString(R.string.years), "1", false);
            addCurrencyField(inputs);
        } else {
            addField(inputs, "initial", getString(R.string.amount), "10000", true);
            addField(inputs, "rate", getString(R.string.annual_inflation), "3", true);
            addField(inputs, "years", getString(R.string.years), "10", false);
            addCurrencyField(inputs);
        }
        addSpace(inputs, 10);
        TextView calculate = button(getString(R.string.calculate), true);
        calculate.setOnClickListener(v -> calculateAndShow());
        inputs.addView(calculate, new LinearLayout.LayoutParams(-1, dp(48)));
        calculationError = text("", 12, R.color.coral, false);
        inputs.addView(calculationError, spacedParams(5));
        applyPendingInputs();
    }

    private void addCurrencyField(LinearLayout parent) {
        addLabel(parent, getString(R.string.currency));
        currencySpinner = new Spinner(this);
        String[] codes = {"USD", "EUR", "KZT", "RUB", "CNY", "JPY"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, codes);
        currencySpinner.setAdapter(adapter); parent.addView(currencySpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        addSpace(parent, 9);
    }

    private void addPaymentType(LinearLayout parent) {
        addLabel(parent, getString(R.string.payment_type));
        paymentSpinner = new Spinner(this);
        paymentSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{getString(R.string.annuity), getString(R.string.differentiated)}));
        parent.addView(paymentSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        addSpace(parent, 9);
    }

    private void addField(LinearLayout parent, String key, String label, String initial, boolean decimal) {
        addLabel(parent, label);
        EditText input = new EditText(this);
        input.setText(initial); input.setSingleLine(true); input.setTextSize(16); input.setTextColor(color(R.color.ink));
        input.setHint(label);
        input.setPadding(dp(12), 0, dp(12), 0); input.setSelectAllOnFocus(true);
        input.setInputType(decimal ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED
                : InputType.TYPE_CLASS_NUMBER);
        input.setBackground(round(R.color.canvas, R.color.line, 10));
        fields.put(key, input); parent.addView(input, new LinearLayout.LayoutParams(-1, dp(48)));
        addSpace(parent, 10);
    }

    private void calculateAndShow() {
        String oldSummary = resultSummary;
        String oldSnapshot = inputsSnapshot;
        String oldCurrency = resultCurrency;
        List<LoanEngine.PaymentRow> oldSchedule = currentSchedule;
        long oldScenarioId = currentScenarioId;
        boolean oldScenarioFavorite = currentScenarioFavorite;
        TextView oldFavoriteButton = resultFavoriteButton;
        try {
            hideKeyboard();
            if (calculationError != null) calculationError.setText("");
            LinearLayout result = card(); result.setOrientation(LinearLayout.VERTICAL); result.setPadding(dp(16), dp(17), dp(16), dp(17));
            addResultHeading(result);
            if (calculator.equals(LOAN) || calculator.equals(MORTGAGE) || calculator.equals(EARLY)) showLoanResult(result);
            else if (calculator.equals(SAVINGS)) showSavingsResult(result);
            else if (calculator.equals(DEPOSIT)) showDepositResult(result);
            else showInflationResult(result);
            addSpace(result, 15);
            if (calculator.equals(DEPOSIT)) {
                result.addView(text(getString(R.string.deposit_calculation_note), 11, R.color.muted, false));
                addSpace(result, 10);
            }
            result.addView(text(getString(R.string.assumptions), 11, R.color.muted, false));
            addSpace(result, 5);
            result.addView(text(getString(R.string.finance_note), 11, R.color.muted, false));
            addSpace(result, 13);
            long savedId = scenarioStore.save(calculator, resultCurrency, inputsSnapshot, resultSummary);
            currentScenarioId = savedId;
            currentScenarioFavorite = false;
            updateResultFavoriteButton();
            if (currentResultCard != null && currentResultCard.getParent() == formBody) formBody.removeView(currentResultCard);
            formBody.addView(result, spacedParams(14));
            currentResultCard = result;
            formScroll.post(() -> formScroll.smoothScrollTo(0, Math.max(0, result.getTop() - dp(16))));
        } catch (RuntimeException e) {
            resultSummary = oldSummary;
            inputsSnapshot = oldSnapshot;
            resultCurrency = oldCurrency;
            currentSchedule = oldSchedule;
            currentScenarioId = oldScenarioId;
            currentScenarioFavorite = oldScenarioFavorite;
            resultFavoriteButton = oldFavoriteButton;
            Log.e("Finora", "Calculation failed", e);
            if (calculationError != null) calculationError.setText(getString(R.string.invalid_input));
            if (formScroll != null && formInputs != null && calculationError != null) {
                formScroll.post(() -> {
                    int target = formInputs.getTop() + calculationError.getBottom() - formScroll.getHeight() + dp(48);
                    formScroll.smoothScrollTo(0, Math.max(0, target));
                });
            }
        }
    }

    private void addResultHeading(LinearLayout parent) {
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text(getString(R.string.results), 12, R.color.brand_green, true), new LinearLayout.LayoutParams(0, -2, 1));
        resultFavoriteButton = text("☆", 28, R.color.muted, true);
        resultFavoriteButton.setGravity(Gravity.CENTER);
        resultFavoriteButton.setMinWidth(dp(42));
        resultFavoriteButton.setMinHeight(dp(42));
        resultFavoriteButton.setOnClickListener(v -> toggleResultFavorite());
        heading.addView(resultFavoriteButton);
        parent.addView(heading);
        addSpace(parent, 8);
    }

    private void toggleResultFavorite() {
        if (currentScenarioId < 0) return;
        currentScenarioFavorite = !currentScenarioFavorite;
        scenarioStore.setFavorite(currentScenarioId, currentScenarioFavorite);
        updateResultFavoriteButton();
    }

    private void updateResultFavoriteButton() {
        if (resultFavoriteButton == null) return;
        resultFavoriteButton.setText(currentScenarioFavorite ? "★" : "☆");
        resultFavoriteButton.setTextColor(color(currentScenarioFavorite ? R.color.brand_green : R.color.muted));
        resultFavoriteButton.setContentDescription(getString(currentScenarioFavorite ? R.string.remove_favorite : R.string.add_favorite));
    }

    private void showLoanResult(LinearLayout result) {
        int scale = 2;
        String currency = currencySpinner.getSelectedItem().toString();
        resultCurrency = currency; scale = currencyScale(currency);
        BigDecimal rate = number("rate");
        int months = loanMonths("years");
        BigDecimal principal;
        if (calculator.equals(MORTGAGE)) principal = number("homePrice").subtract(number("amount"));
        else principal = number("amount");
        if (principal.signum() < 0) throw new IllegalArgumentException();
        LoanEngine.PaymentType type = paymentSpinner != null && paymentSpinner.getSelectedItemPosition() == 1
                ? LoanEngine.PaymentType.DIFFERENTIATED : LoanEngine.PaymentType.ANNUITY;
        BigDecimal extra = calculator.equals(EARLY) ? number("extra") : BigDecimal.ZERO;
        LoanEngine.Result loan = LoanEngine.calculate(principal, rate, months, type,
                fields.containsKey("fees") ? number("fees") : BigDecimal.ZERO,
                fields.containsKey("monthlyCosts") ? number("monthlyCosts") : BigDecimal.ZERO,
                extra, null, scale);
        currentSchedule = new ArrayList<>(loan.schedule);
        String monthlyTitle = calculator.equals(EARLY) ? getString(R.string.months_to_repay) : getString(R.string.monthly_payment);
        BigDecimal primary = calculator.equals(EARLY) ? BigDecimal.valueOf(loan.actualMonths()) : loan.firstPayment();
        String primaryValue = calculator.equals(EARLY) ? String.valueOf(loan.actualMonths()) : money(primary, currency);
        bigMetric(result, monthlyTitle, primaryValue, calculator.equals(EARLY) ? getString(R.string.month) : getString(R.string.per_month));
        addSpace(result, 12);
        BigDecimal displayedCost = loan.totalCost;
        if (calculator.equals(MORTGAGE)) displayedCost = displayedCost.add(number("amount")).setScale(scale, java.math.RoundingMode.HALF_UP);
        String interestLabel = loan.totalInterest.signum() == 0 ? getString(R.string.no_interest) : getString(R.string.total_interest);
        addMetricPair(result, interestLabel, money(loan.totalInterest, currency),
                getString(R.string.total_cost), money(displayedCost, currency));
        addMetricPair(result, getString(R.string.total_paid), money(loan.totalPaid, currency),
                getString(R.string.loan_principal), money(loan.principal, currency));
        if (calculator.equals(MORTGAGE)) addMetricPair(result, getString(R.string.down_payment), money(number("amount"), currency),
                getString(R.string.mortgage_amount), money(principal, currency));
        if (calculator.equals(EARLY)) addMetricPair(result, getString(R.string.term_years), String.valueOf(value("years")),
                getString(R.string.extra_monthly), money(extra, currency));
        if (fields.containsKey("fees") || fields.containsKey("monthlyCosts")) {
            addSpace(result, 5); result.addView(text(getString(R.string.costs_included), 11, R.color.muted, false));
        }
        addSpace(result, 14); result.addView(text(getString(R.string.loan_chart), 13, R.color.ink, true));
        List<BigDecimal> balances = new ArrayList<>();
        balances.add(principal);
        int stride = Math.max(1, loan.schedule.size() / 12);
        for (int i = stride - 1; i < loan.schedule.size(); i += stride) balances.add(loan.schedule.get(i).balance);
        if (loan.schedule.size() > 0 && balances.get(balances.size() - 1).signum() != 0) balances.add(loan.schedule.get(loan.schedule.size() - 1).balance);
        result.addView(new MiniChartView(this, balances, false), new LinearLayout.LayoutParams(-1, dp(118)));
        addSpace(result, 6); result.addView(text(getString(R.string.schedule), 13, R.color.ink, true));
        addSpace(result, 5); result.addView(text(getString(R.string.schedule_note), 10, R.color.muted, false));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(0, dp(9), 0, dp(7));
        header.addView(text(getString(R.string.month), 9, R.color.muted, true), new LinearLayout.LayoutParams(dp(58), -2));
        header.addView(text(getString(R.string.payment), 9, R.color.muted, true), new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(text(getString(R.string.balance), 9, R.color.muted, true)); result.addView(header);
        addSchedule(result, currency);
        resultSummary = money(displayedCost, currency);
        inputsSnapshot = snapshotInputs();
    }

    private void addSchedule(LinearLayout parent, String currency) {
        LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        parent.addView(rows, new LinearLayout.LayoutParams(-1, -2));
        scheduleVisibleCount = Math.min(10, currentSchedule.size());
        appendScheduleRows(rows, currency, 0, scheduleVisibleCount);
        addScheduleMore(rows, currency);
    }

    private void appendScheduleRows(LinearLayout rows, String currency, int start, int end) {
        java.time.format.DateTimeFormatter dateFormat = java.time.format.DateTimeFormatter.ofPattern("MMM yy", locale());
        for (int i = start; i < end; i++) {
            LoanEngine.PaymentRow row = currentSchedule.get(i);
            LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL); line.setPadding(0, dp(5), 0, dp(5));
            String monthLabel = row.month + " · " + row.date.format(dateFormat);
            line.addView(text(monthLabel, 9, R.color.muted, false), new LinearLayout.LayoutParams(dp(58), -2));
            LinearLayout values = new LinearLayout(this); values.setOrientation(LinearLayout.VERTICAL);
            values.addView(text(money(row.payment, currency), 12, R.color.ink, true));
            values.addView(text(getString(R.string.principal_paid) + " " + money(row.principal, currency) + "  ·  " + getString(R.string.interest) + " " + money(row.interest, currency), 9, R.color.muted, false));
            line.addView(values, new LinearLayout.LayoutParams(0, -2, 1));
            line.addView(text(money(row.balance, currency), 10, R.color.muted, false));
            rows.addView(line); addDivider(rows);
        }
    }

    private void addScheduleMore(LinearLayout rows, String currency) {
        if (scheduleVisibleCount >= currentSchedule.size()) return;
        TextView more = text(getString(R.string.show_schedule) + "  ·  " + currentSchedule.size(), 12, R.color.brand_green, true);
        more.setPadding(0, dp(8), 0, dp(2));
        more.setOnClickListener(v -> {
            rows.removeView(more);
            int end = Math.min(scheduleVisibleCount + 20, currentSchedule.size());
            appendScheduleRows(rows, currency, scheduleVisibleCount, end);
            scheduleVisibleCount = end;
            addScheduleMore(rows, currency);
        });
        rows.addView(more);
    }

    private void showSavingsResult(LinearLayout result) {
        String currency = currencySpinner.getSelectedItem().toString(); resultCurrency = currency;
        ProjectionEngine.Result projection = ProjectionEngine.savings(number("initial"), number("monthly"), number("rate"), years("years"), currencyScale(currency));
        bigMetric(result, getString(R.string.future_value), money(projection.endingBalance, currency), null);
        addSpace(result, 12); addMetricPair(result, getString(R.string.contributions), money(projection.contributions, currency),
                getString(R.string.growth), money(projection.growth, currency));
        addSpace(result, 14); result.addView(text(getString(R.string.growth_chart), 13, R.color.ink, true));
        result.addView(new MiniChartView(this, projection.yearlyBalances, true), new LinearLayout.LayoutParams(-1, dp(118)));
        addSpace(result, 3); result.addView(text(getString(R.string.years) + ": " + years("years") + " " + getString(R.string.years_short), 10, R.color.muted, false));
        resultSummary = money(projection.endingBalance, currency); inputsSnapshot = snapshotInputs();
    }

    private void showDepositResult(LinearLayout result) {
        String currency = currencySpinner.getSelectedItem().toString();
        resultCurrency = currency;
        BigDecimal nominalRate = number("rate");
        ProjectionEngine.DepositResult projection = ProjectionEngine.deposit(
                number("initial"), number("monthly"), nominalRate, optionalNumber("effectiveRate"),
                years("years"), currencyScale(currency));
        bigMetric(result, getString(R.string.deposit_final_balance), money(projection.endingBalance, currency), null);
        addSpace(result, 12);
        addMetricPair(result, getString(R.string.contributions), money(projection.contributions, currency),
                getString(R.string.deposit_interest_earned), money(projection.interestEarned, currency));
        addMetricPair(result, getString(R.string.deposit_nominal_rate), percent(nominalRate),
                getString(R.string.deposit_effective_result), percent(projection.effectiveAnnualRatePercent));
        addSpace(result, 14);
        result.addView(text(getString(R.string.growth_chart), 13, R.color.ink, true));
        result.addView(new MiniChartView(this, projection.yearlyBalances, true), new LinearLayout.LayoutParams(-1, dp(118)));
        addSpace(result, 3);
        result.addView(text(getString(R.string.years) + ": " + years("years") + " " + getString(R.string.years_short), 10, R.color.muted, false));
        resultSummary = money(projection.endingBalance, currency);
        inputsSnapshot = snapshotInputs();
    }

    private void showInflationResult(LinearLayout result) {
        String currency = currencySpinner.getSelectedItem().toString(); resultCurrency = currency;
        BigDecimal amount = number("initial"); int years = years("years");
        BigDecimal today = ProjectionEngine.purchasingPower(amount, number("rate"), years, currencyScale(currency));
        bigMetric(result, getString(R.string.today_value), money(today, currency), null);
        addSpace(result, 12); addMetricPair(result, getString(R.string.amount), money(amount, currency),
                getString(R.string.purchasing_power), amount.signum() == 0 ? "0%" : amount.subtract(today).multiply(new BigDecimal("100")).divide(amount, 1, java.math.RoundingMode.HALF_UP) + "%");
        resultSummary = money(today, currency); inputsSnapshot = snapshotInputs();
    }

    private void buildHistory() {
        LinearLayout body = bodyOf(scrollPage());
        pageHeader(body, getString(R.string.history), getString(R.string.history_subtitle));
        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        TextView allFilter = historyFilter(getString(R.string.all_history), !showFavoritesOnly);
        TextView favoriteFilter = historyFilter(getString(R.string.favorites), showFavoritesOnly);
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        filters.addView(allFilter, filterParams);
        LinearLayout.LayoutParams favoriteParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        favoriteParams.leftMargin = dp(8);
        filters.addView(favoriteFilter, favoriteParams);
        body.addView(filters, spacedParams(2));
        allFilter.setOnClickListener(v -> { if (showFavoritesOnly) { showFavoritesOnly = false; showPage(PAGE_HISTORY); } });
        favoriteFilter.setOnClickListener(v -> { if (!showFavoritesOnly) { showFavoritesOnly = true; showPage(PAGE_HISTORY); } });

        List<ScenarioStore.Scenario> saved = scenarioStore.all(showFavoritesOnly);
        if (saved.isEmpty()) {
            LinearLayout empty = card(); empty.setOrientation(LinearLayout.VERTICAL); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(24), dp(28), dp(24), dp(28));
            ImageView image = icon(R.drawable.ic_history); empty.addView(image, new LinearLayout.LayoutParams(dp(42), dp(42)));
            addSpace(empty, 12); empty.addView(text(getString(showFavoritesOnly ? R.string.no_favorites : R.string.no_saved), 14, R.color.muted, false));
            body.addView(empty, spacedParams(14));
            if (!showFavoritesOnly) {
                TextView clear = button(getString(R.string.clear_history), false);
                clear.setOnClickListener(v -> clearHistory());
                body.addView(clear, spacedParams(12));
            }
            return;
        }
        for (ScenarioStore.Scenario scenario : saved) {
            LinearLayout tile = card(); tile.setOrientation(LinearLayout.VERTICAL); tile.setPadding(dp(15), dp(14), dp(15), dp(14));
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
            copy.addView(text(scenarioLabel(scenario.kind), 15, R.color.ink, true));
            String date = new SimpleDateFormat("yyyy-MM-dd · HH:mm", locale()).format(new Date(scenario.createdAt));
            copy.addView(text(date + "  ·  " + scenario.currency, 10, R.color.muted, false));
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(text(scenario.result, 14, R.color.brand_green, true));
            TextView star = text(scenario.favorite ? "★" : "☆", 25, scenario.favorite ? R.color.brand_green : R.color.muted, true);
            star.setGravity(Gravity.CENTER);
            star.setMinWidth(dp(42)); star.setMinHeight(dp(42));
            star.setContentDescription(getString(scenario.favorite ? R.string.remove_favorite : R.string.add_favorite));
            star.setOnClickListener(v -> {
                scenarioStore.setFavorite(scenario.id, !scenario.favorite);
                showPage(PAGE_HISTORY);
            });
            row.addView(star);
            tile.addView(row);
            addSpace(tile, 6); tile.addView(text(displayInputs(scenario.inputs, scenario.kind), 11, R.color.muted, false));
            tile.setOnClickListener(v -> { calculator = scenario.kind; returnPage = PAGE_HISTORY; pendingScenarioInputs = scenario.inputs; showPage(PAGE_FORM); });
            addSpace(tile, 7);
            TextView remove = text(getString(R.string.remove), 11, R.color.coral, true);
            remove.setGravity(Gravity.END); remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.delete_scenario)).setNegativeButton(getString(R.string.cancel), null)
                    .setPositiveButton(getString(R.string.delete), (d, w) -> { scenarioStore.remove(scenario.id); showPage(PAGE_HISTORY); }).show());
            tile.addView(remove); body.addView(tile, spacedParams(10));
        }
        TextView clear = button(getString(R.string.clear_history), false); clear.setOnClickListener(v -> clearHistory());
        body.addView(clear, spacedParams(12));
    }

    private TextView historyFilter(String label, boolean selected) {
        TextView filter = text(label, 13, selected ? R.color.brand_navy : R.color.muted, selected);
        filter.setGravity(Gravity.CENTER);
        filter.setBackground(round(selected ? R.color.brand_mint : R.color.surface, selected ? R.color.brand_mint : R.color.line, 12));
        return filter;
    }

    private void clearHistory() {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.clear_all_confirm)).setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.confirm), (d, w) -> { scenarioStore.clear(); showPage(PAGE_HISTORY); }).show();
    }

    private String snapshotInputs() {
        StringBuilder result = new StringBuilder();
        for (java.util.Map.Entry<String, EditText> item : fields.entrySet()) {
            if (result.length() > 0) result.append(';');
            result.append(item.getKey()).append('=').append(item.getValue().getText().toString().trim());
        }
        if (currencySpinner != null) result.append(";currency=").append(currencySpinner.getSelectedItem());
        if (paymentSpinner != null) result.append(";payment=").append(paymentSpinner.getSelectedItemPosition());
        return result.toString();
    }

    private void applyPendingInputs() {
        if (pendingScenarioInputs == null) return;
        for (String part : pendingScenarioInputs.split(";")) {
            int split = part.indexOf('='); if (split < 1) continue;
            String key = part.substring(0, split), value = part.substring(split + 1);
            if (fields.containsKey(key)) fields.get(key).setText(value);
            else if (key.equals("currency") && currencySpinner != null) {
                for (int i = 0; i < currencySpinner.getCount(); i++) if (currencySpinner.getItemAtPosition(i).toString().equals(value)) currencySpinner.setSelection(i);
            } else if (key.equals("payment") && paymentSpinner != null) {
                try { paymentSpinner.setSelection(Integer.parseInt(value)); } catch (NumberFormatException ignored) { }
            }
        }
        pendingScenarioInputs = null;
    }

    private String displayInputs(String serialized, String scenarioKind) {
        StringBuilder display = new StringBuilder();
        for (String part : serialized.split(";")) {
            int split = part.indexOf('='); if (split < 1) continue;
            String key = part.substring(0, split), value = part.substring(split + 1);
            String label;
            switch (key) {
                case "amount": label = getString(R.string.amount); break;
                case "homePrice": label = getString(R.string.home_price); break;
                case "rate": label = scenarioKind.equals(DEPOSIT) ? getString(R.string.deposit_nominal_rate) : getString(R.string.annual_rate); break;
                case "effectiveRate": label = getString(R.string.deposit_effective_rate); break;
                case "rateA": case "rateB": label = getString(R.string.annual_rate); break;
                case "years": label = getString(R.string.term_years); break;
                case "extra": label = getString(R.string.extra_monthly); break;
                case "initial": label = getString(scenarioKind.equals(DEPOSIT) ? R.string.deposit_initial : R.string.initial_balance); break;
                case "monthly": label = getString(R.string.monthly_contribution); break;
                case "fees": label = getString(R.string.fees); break;
                case "monthlyCosts": label = getString(R.string.monthly_costs); break;
                case "currency": label = getString(R.string.currency); break;
                case "payment": continue;
                default: label = key;
            }
            if (value.isEmpty()) continue;
            if (display.length() > 0) display.append(" · ");
            display.append(label).append(": ").append(value);
        }
        return display.toString();
    }

    private void buildNavigation() {
        navBar.removeAllViews();
        int[] destinations = {PAGE_HOME, PAGE_HISTORY};
        int[] icons = {R.drawable.ic_home, R.drawable.ic_history};
        int[] labels = {R.string.home, R.string.history};
        for (int i = 0; i < destinations.length; i++) {
            final int destination = destinations[i];
            LinearLayout item = new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER);
            ImageView image = new ImageView(this); image.setImageResource(icons[i]); image.setColorFilter(color(selectedTab == destination ? R.color.brand_green : R.color.muted));
            item.addView(image, new LinearLayout.LayoutParams(dp(21), dp(21)));
            TextView label = text(getString(labels[i]), 9, selectedTab == destination ? R.color.brand_green : R.color.muted, selectedTab == destination);
            label.setGravity(Gravity.CENTER); item.addView(label);
            navBar.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
            item.setOnClickListener(v -> showPage(destination));
        }
    }

    private void openCalculator(String type) { calculator = type; returnPage = PAGE_HOME; selectedTab = PAGE_HOME; showPage(PAGE_FORM); }
    private String calculatorTitle(String type) {
        if (type.equals(MORTGAGE)) return getString(R.string.mortgage);
        if (type.equals(EARLY)) return getString(R.string.early_repayment);
        if (type.equals(SAVINGS)) return getString(R.string.savings);
        if (type.equals(DEPOSIT)) return getString(R.string.deposit);
        if (type.equals(INFLATION)) return getString(R.string.inflation);
        return getString(R.string.loan);
    }
    private String calculatorSubtitle(String type) {
        if (type.equals(MORTGAGE)) return getString(R.string.mortgage_desc);
        if (type.equals(EARLY)) return getString(R.string.early_desc);
        if (type.equals(SAVINGS)) return getString(R.string.savings_desc);
        if (type.equals(DEPOSIT)) return getString(R.string.deposit_desc);
        if (type.equals(INFLATION)) return getString(R.string.inflation_desc);
        return getString(R.string.loan_desc);
    }
    private String scenarioLabel(String type) {
        if (type.equals(MORTGAGE)) return getString(R.string.scenario_type_mortgage);
        if (type.equals(EARLY)) return getString(R.string.scenario_type_early);
        if (type.equals(SAVINGS)) return getString(R.string.scenario_type_savings);
        if (type.equals(DEPOSIT)) return getString(R.string.scenario_type_deposit);
        if (type.equals(INFLATION)) return getString(R.string.scenario_type_inflation);
        return getString(R.string.scenario_type_loan);
    }

    private void pageHeader(LinearLayout body, String title, String subtitle) {
        body.addView(text(title, 26, R.color.ink, true));
        if (subtitle != null && !subtitle.isEmpty()) { addSpace(body, 5); body.addView(text(subtitle, 13, R.color.muted, false)); }
        addSpace(body, 16);
    }
    private void addLabel(LinearLayout parent, String label) {
        TextView view = text(label, 12, R.color.muted, true); parent.addView(view); addSpace(parent, 5);
    }
    private void bigMetric(LinearLayout parent, String title, String value, String suffix) {
        TextView label = text(title, 12, R.color.muted, false); parent.addView(label);
        LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.BOTTOM);
        TextView number = text(value, 27, R.color.ink, true); line.addView(number);
        if (suffix != null) { TextView unit = text(" " + suffix, 11, R.color.muted, false); unit.setPadding(0, 0, 0, dp(4)); line.addView(unit); }
        parent.addView(line);
    }
    private void addMetricPair(LinearLayout parent, String leftLabel, String leftValue, String rightLabel, String rightValue) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.TOP);
        LinearLayout a = new LinearLayout(this); a.setOrientation(LinearLayout.VERTICAL); a.addView(text(leftLabel, 10, R.color.muted, false)); addSpace(a, 3); a.addView(text(leftValue, 14, R.color.ink, true));
        LinearLayout b = new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.addView(text(rightLabel, 10, R.color.muted, false)); addSpace(b, 3); b.addView(text(rightValue, 14, R.color.ink, true));
        row.addView(a, new LinearLayout.LayoutParams(0, -2, 1)); row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(row, spacedParams(9));
    }
    private void addDivider(LinearLayout parent) {
        View line = new View(this); line.setBackgroundColor(color(R.color.line));
        parent.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }
    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(R.color.surface, R.color.line, 16)); return card;
    }
    private ImageView icon(int resource) {
        ImageView view = new ImageView(this); view.setImageResource(resource); view.setBackground(round(R.color.brand_mint, R.color.brand_mint, 12));
        view.setPadding(dp(8), dp(8), dp(8), dp(8)); return view;
    }
    private TextView button(String label, boolean primary) {
        TextView view = text(label, 14, primary ? android.R.color.white : R.color.brand_navy, true);
        view.setGravity(Gravity.CENTER); view.setPadding(dp(15), dp(9), dp(15), dp(9));
        view.setBackground(round(primary ? R.color.brand_navy : R.color.brand_mint, primary ? R.color.brand_navy : R.color.brand_mint, 12));
        return view;
    }
    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(color(color));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        view.setIncludeFontPadding(false); return view;
    }
    private android.graphics.drawable.GradientDrawable round(int fillColor, int strokeColor, int radius) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color(fillColor)); shape.setCornerRadius(dp(radius));
        if (fillColor != strokeColor) shape.setStroke(dp(1), color(strokeColor));
        return shape;
    }
    private LinearLayout.LayoutParams spacedParams(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(top); return params;
    }
    private void addSpace(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private int color(int res) { return getResources().getColor(res); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String value(String key) { return fields.get(key).getText().toString().trim().replace(',', '.'); }
    private BigDecimal number(String key) {
        String value = value(key);
        if (value.isEmpty()) throw new IllegalArgumentException();
        return new BigDecimal(value);
    }
    private BigDecimal optionalNumber(String key) {
        String value = value(key);
        return value.isEmpty() ? null : new BigDecimal(value);
    }
    private String percent(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString() + "%";
    }
    private int loanMonths(String key) {
        BigDecimal termYears = number(key);
        if (termYears.signum() <= 0 || termYears.compareTo(new BigDecimal("35")) > 0) throw new IllegalArgumentException("Term must be between 1 month and 35 years");
        return termYears.multiply(new BigDecimal("12")).setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
    }
    private int years(String key) { int n = Integer.parseInt(value(key)); if (n < 0 || n > 100) throw new IllegalArgumentException(); return n; }
    private String money(BigDecimal value, String currency) {
        NumberFormat format = NumberFormat.getCurrencyInstance(locale());
        format.setCurrency(Currency.getInstance(currency)); format.setMinimumFractionDigits(currencyScale(currency)); format.setMaximumFractionDigits(currencyScale(currency));
        return format.format(value);
    }
    private int currencyScale(String currency) { return currency.equals("JPY") ? 0 : 2; }
    @SuppressWarnings("deprecation") private Locale locale() { return getResources().getConfiguration().locale; }
    private String languageShort() {
        String language = getSharedPreferences("finora", MODE_PRIVATE).getString("language", "en");
        if (language.equals("ru")) return "РУС"; if (language.equals("zh")) return "中文"; return "EN";
    }
    private Locale localeFor(String language) {
        if ("ru".equals(language)) return new Locale("ru", "RU");
        if ("zh".equals(language)) return Locale.SIMPLIFIED_CHINESE;
        return Locale.ENGLISH;
    }
    private void showLanguageMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(getString(R.string.language_en)).setOnMenuItemClickListener(i -> changeLanguage("en"));
        popup.getMenu().add(getString(R.string.language_ru)).setOnMenuItemClickListener(i -> changeLanguage("ru"));
        popup.getMenu().add(getString(R.string.language_zh)).setOnMenuItemClickListener(i -> changeLanguage("zh"));
        popup.show();
    }
    private boolean changeLanguage(String language) {
        getSharedPreferences("finora", MODE_PRIVATE).edit().putString("language", language).apply(); recreate(); return true;
    }
    private void hideKeyboard() {
        try { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(), 0); } catch (Exception ignored) { }
    }

    /** Lightweight line plot drawn locally; values are sampled from the full calculation series. */
    private final class MiniChartView extends View {
        private final List<BigDecimal> values;
        private final boolean yearly;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        MiniChartView(Context context, List<BigDecimal> values, boolean yearly) {
            super(context); this.values = values; this.yearly = yearly;
            setContentDescription(getString(yearly ? R.string.year_label : R.string.month) + ", " + getString(R.string.cumulative));
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = dp(3), right = getWidth() - dp(3), top = dp(9), bottom = getHeight() - dp(9);
            paint.setStrokeWidth(dp(1)); paint.setColor(color(R.color.line)); paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 3; i++) { float y = top + (bottom - top) * i / 2f; canvas.drawLine(left, y, right, y, paint); }
            if (values == null || values.size() < 2) return;
            BigDecimal min = values.get(0), max = values.get(0);
            for (BigDecimal v : values) { if (v.compareTo(min) < 0) min = v; if (v.compareTo(max) > 0) max = v; }
            BigDecimal span = max.subtract(min);
            if (span.signum() == 0) span = BigDecimal.ONE;
            path.reset();
            for (int i = 0; i < values.size(); i++) {
                float x = left + (right - left) * i / (values.size() - 1f);
                double fraction = values.get(i).subtract(min).divide(span, java.math.MathContext.DECIMAL64).doubleValue();
                float y = bottom - (float) (fraction * (bottom - top));
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            paint.setColor(color(R.color.brand_green)); paint.setStrokeWidth(dp(3)); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(color(R.color.brand_green));
            BigDecimal last = values.get(values.size() - 1);
            float y = bottom - (float) (last.subtract(min).divide(span, java.math.MathContext.DECIMAL64).doubleValue() * (bottom - top));
            canvas.drawCircle(right, y, dp(4), paint);
        }
    }
}
