package com.finora.app.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Compound growth and purchasing power calculations, isolated from Android UI. */
public final class ProjectionEngine {
    private static final MathContext MC = new MathContext(28, RoundingMode.HALF_EVEN);
    private static final BigDecimal TWELVE_HUNDRED = new BigDecimal("1200");

    private ProjectionEngine() { }

    public static final class Result {
        public final BigDecimal endingBalance;
        public final BigDecimal contributions;
        public final BigDecimal growth;
        public final List<BigDecimal> yearlyBalances;
        private Result(BigDecimal endingBalance, BigDecimal contributions, BigDecimal growth, List<BigDecimal> yearlyBalances) {
            this.endingBalance = endingBalance; this.contributions = contributions;
            this.growth = growth; this.yearlyBalances = Collections.unmodifiableList(yearlyBalances);
        }
    }

    public static final class DepositResult {
        public final BigDecimal endingBalance;
        public final BigDecimal contributions;
        public final BigDecimal interestEarned;
        public final BigDecimal effectiveAnnualRatePercent;
        public final List<BigDecimal> yearlyBalances;
        private DepositResult(BigDecimal endingBalance, BigDecimal contributions, BigDecimal interestEarned,
                              BigDecimal effectiveAnnualRatePercent, List<BigDecimal> yearlyBalances) {
            this.endingBalance = endingBalance; this.contributions = contributions;
            this.interestEarned = interestEarned; this.effectiveAnnualRatePercent = effectiveAnnualRatePercent;
            this.yearlyBalances = Collections.unmodifiableList(yearlyBalances);
        }
    }

    /** Monthly rate is derived from the entered nominal annual rate; each contribution is added at month end. */
    public static Result savings(BigDecimal startingAmount, BigDecimal monthlyContribution,
                                 BigDecimal annualReturnPercent, int years, int currencyScale) {
        validateScale(currencyScale);
        requireNonNegative(startingAmount, "starting amount");
        requireNonNegative(monthlyContribution, "monthly contribution");
        if (annualReturnPercent == null || annualReturnPercent.compareTo(new BigDecimal("1000")) > 0
                || annualReturnPercent.compareTo(new BigDecimal("-1200")) <= 0) {
            throw new IllegalArgumentException("Annual return must be above -1200% and at most 1000%");
        }
        checkDecimal(annualReturnPercent, "annual return");
        if (years < 0 || years > 100) throw new IllegalArgumentException("Years must be between 0 and 100");
        BigDecimal monthlyRate = annualReturnPercent.divide(TWELVE_HUNDRED, MC);
        BigDecimal balance = startingAmount;
        BigDecimal contributions = startingAmount;
        List<BigDecimal> yearly = new ArrayList<>();
        yearly.add(balance.setScale(currencyScale, RoundingMode.HALF_UP));
        for (int month = 1; month <= years * 12; month++) {
            balance = balance.multiply(BigDecimal.ONE.add(monthlyRate, MC), MC).add(monthlyContribution, MC);
            contributions = contributions.add(monthlyContribution);
            if (month % 12 == 0) yearly.add(balance.setScale(currencyScale, RoundingMode.HALF_UP));
        }
        BigDecimal rounded = balance.setScale(currencyScale, RoundingMode.HALF_UP);
        BigDecimal contributed = contributions.setScale(currencyScale, RoundingMode.HALF_UP);
        return new Result(rounded, contributed, rounded.subtract(contributed), yearly);
    }

    /**
     * Projects a monthly-funded bank deposit. If no effective rate is supplied, the nominal annual rate
     * is divided into monthly periods and its effective annual equivalent is calculated.
     * Contributions are added at the end of each month.
     */
    public static DepositResult deposit(BigDecimal startingAmount, BigDecimal monthlyContribution,
                                        BigDecimal nominalAnnualRatePercent, BigDecimal effectiveAnnualRatePercent,
                                        int years, int currencyScale) {
        validateScale(currencyScale);
        requireNonNegative(startingAmount, "starting amount");
        requireNonNegative(monthlyContribution, "monthly contribution");
        validateDepositRate(nominalAnnualRatePercent, "nominal annual rate");
        if (effectiveAnnualRatePercent != null) validateDepositRate(effectiveAnnualRatePercent, "effective annual rate");
        if (years < 0 || years > 100) throw new IllegalArgumentException("Years must be between 0 and 100");

        BigDecimal monthlyRate;
        BigDecimal effectiveRate;
        if (effectiveAnnualRatePercent == null) {
            monthlyRate = nominalAnnualRatePercent.divide(TWELVE_HUNDRED, MC);
            double annualFactor = Math.pow(1.0 + monthlyRate.doubleValue(), 12.0);
            effectiveRate = BigDecimal.valueOf((annualFactor - 1.0) * 100.0).round(MC);
        } else {
            effectiveRate = effectiveAnnualRatePercent;
            double annualFactor = 1.0 + effectiveRate.movePointLeft(2).doubleValue();
            monthlyRate = BigDecimal.valueOf(Math.pow(annualFactor, 1.0 / 12.0) - 1.0).round(MC);
        }

        BigDecimal balance = startingAmount;
        BigDecimal contributions = startingAmount;
        List<BigDecimal> yearly = new ArrayList<>();
        yearly.add(balance.setScale(currencyScale, RoundingMode.HALF_UP));
        for (int month = 1; month <= years * 12; month++) {
            balance = balance.multiply(BigDecimal.ONE.add(monthlyRate, MC), MC).add(monthlyContribution, MC);
            contributions = contributions.add(monthlyContribution);
            if (month % 12 == 0) yearly.add(balance.setScale(currencyScale, RoundingMode.HALF_UP));
        }
        BigDecimal roundedBalance = balance.setScale(currencyScale, RoundingMode.HALF_UP);
        BigDecimal roundedContributions = contributions.setScale(currencyScale, RoundingMode.HALF_UP);
        return new DepositResult(roundedBalance, roundedContributions,
                roundedBalance.subtract(roundedContributions), effectiveRate, yearly);
    }

    /** Returns today's equivalent of an amount after annual inflation compounds for a whole number of years. */
    public static BigDecimal purchasingPower(BigDecimal amount, BigDecimal annualInflationPercent,
                                             int years, int currencyScale) {
        validateScale(currencyScale);
        requireNonNegative(amount, "amount");
        if (annualInflationPercent == null || annualInflationPercent.compareTo(new BigDecimal("-100")) <= 0
                || annualInflationPercent.compareTo(new BigDecimal("1000")) > 0) {
            throw new IllegalArgumentException("Inflation must be above -100% and at most 1000%");
        }
        checkDecimal(annualInflationPercent, "inflation");
        if (years < 0 || years > 100) throw new IllegalArgumentException("Years must be between 0 and 100");
        BigDecimal factor = BigDecimal.ONE.add(annualInflationPercent.movePointLeft(2), MC).pow(years, MC);
        return amount.divide(factor, MC).setScale(currencyScale, RoundingMode.HALF_UP);
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        checkDecimal(value, name);
        if (value.signum() < 0 || value.compareTo(new BigDecimal("1000000000000000000")) > 0)
            throw new IllegalArgumentException(name + " must be between zero and 10^18");
    }
    private static void validateDepositRate(BigDecimal value, String name) {
        checkDecimal(value, name);
        if (value.signum() < 0 || value.compareTo(new BigDecimal("1000")) > 0)
            throw new IllegalArgumentException(name + " must be between 0% and 1000%");
    }
    private static void checkDecimal(BigDecimal value, String name) {
        if (value == null || value.precision() > 24 || value.scale() > 12 || value.scale() < -12)
            throw new IllegalArgumentException(name + " precision is outside the supported range");
    }
    private static void validateScale(int scale) {
        if (scale < 0 || scale > 4) throw new IllegalArgumentException("Invalid currency scale");
    }
}
