package com.finora.app.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure Java repayment mathematics. No Android classes are used in this package. */
public final class LoanEngine {
    private static final MathContext MC = new MathContext(28, RoundingMode.HALF_EVEN);
    private static final BigDecimal TWELVE_HUNDRED = new BigDecimal("1200");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private LoanEngine() { }

    public enum PaymentType { ANNUITY, DIFFERENTIATED }

    public static final class PaymentRow {
        public final int month;
        public final LocalDate date;
        public final BigDecimal payment;
        public final BigDecimal principal;
        public final BigDecimal interest;
        public final BigDecimal balance;

        private PaymentRow(int month, LocalDate date, BigDecimal payment, BigDecimal principal,
                           BigDecimal interest, BigDecimal balance) {
            this.month = month; this.date = date; this.payment = payment;
            this.principal = principal; this.interest = interest; this.balance = balance;
        }
    }

    public static final class Result {
        public final BigDecimal principal;
        public final BigDecimal plannedPayment;
        public final BigDecimal totalPaid;
        public final BigDecimal totalInterest;
        public final BigDecimal fees;
        public final BigDecimal monthlyCosts;
        public final BigDecimal totalCost;
        public final List<PaymentRow> schedule;

        private Result(BigDecimal principal, BigDecimal plannedPayment, BigDecimal totalPaid,
                       BigDecimal totalInterest, BigDecimal fees, BigDecimal monthlyCosts,
                       BigDecimal totalCost, List<PaymentRow> schedule) {
            this.principal = principal; this.plannedPayment = plannedPayment;
            this.totalPaid = totalPaid; this.totalInterest = totalInterest;
            this.fees = fees; this.monthlyCosts = monthlyCosts; this.totalCost = totalCost;
            this.schedule = Collections.unmodifiableList(schedule);
        }

        public int actualMonths() { return schedule.size(); }
        public BigDecimal firstPayment() {
            return schedule.isEmpty() ? ZERO : schedule.get(0).payment;
        }
    }

    public static BigDecimal annuityPayment(BigDecimal principal, BigDecimal annualRatePercent,
                                            int months, int currencyScale) {
        validate(principal, annualRatePercent, months);
        validateScale(currencyScale);
        BigDecimal p = principal.max(ZERO);
        if (months == 0 || p.signum() == 0) return ZERO.setScale(currencyScale, RoundingMode.HALF_UP);
        BigDecimal rate = annualRatePercent.divide(TWELVE_HUNDRED, MC);
        if (rate.signum() == 0) return p.divide(BigDecimal.valueOf(months), currencyScale, RoundingMode.HALF_UP);
        BigDecimal growth = BigDecimal.ONE.add(rate, MC).pow(months, MC);
        BigDecimal exact = p.multiply(rate, MC).multiply(growth, MC).divide(growth.subtract(BigDecimal.ONE, MC), MC);
        return exact.setScale(currencyScale, RoundingMode.HALF_UP);
    }

    public static Result calculate(BigDecimal principal, BigDecimal annualRatePercent, int months,
                                   PaymentType paymentType, BigDecimal oneTimeFees,
                                   BigDecimal monthlyCosts, BigDecimal extraMonthly,
                                   LocalDate firstPaymentDate, int currencyScale) {
        validate(principal, annualRatePercent, months);
        if (paymentType == null) throw new IllegalArgumentException("Payment type is required");
        validateScale(currencyScale);
        BigDecimal p = checkedAmount(principal, "principal").setScale(currencyScale, RoundingMode.HALF_UP);
        BigDecimal fee = checkedAmount(orZero(oneTimeFees), "fees").setScale(currencyScale, RoundingMode.HALF_UP);
        BigDecimal otherMonthly = checkedAmount(orZero(monthlyCosts), "monthly costs").setScale(currencyScale, RoundingMode.HALF_UP);
        BigDecimal extra = checkedAmount(orZero(extraMonthly), "extra payment").setScale(currencyScale, RoundingMode.HALF_UP);
        BigDecimal unit = BigDecimal.ONE.movePointLeft(currencyScale);
        BigDecimal rate = annualRatePercent.divide(TWELVE_HUNDRED, MC);
        BigDecimal basePayment = paymentType == PaymentType.ANNUITY
                ? annuityPayment(p, annualRatePercent, months, currencyScale)
                : p.divide(BigDecimal.valueOf(months), currencyScale, RoundingMode.HALF_UP);

        List<PaymentRow> rows = new ArrayList<>();
        BigDecimal balance = p;
        BigDecimal interestTotal = ZERO.setScale(currencyScale, RoundingMode.HALF_UP);
        BigDecimal paymentTotal = ZERO.setScale(currencyScale, RoundingMode.HALF_UP);
        LocalDate date = firstPaymentDate == null ? LocalDate.now().plusMonths(1) : firstPaymentDate;
        int guard = 1200;

        for (int i = 1; i <= guard && balance.compareTo(unit.divide(BigDecimal.valueOf(2), currencyScale + 2, RoundingMode.HALF_UP)) > 0; i++) {
            BigDecimal interest = balance.multiply(rate, MC).setScale(currencyScale, RoundingMode.HALF_UP);
            BigDecimal regular = basePayment;
            if (paymentType == PaymentType.DIFFERENTIATED) {
                regular = p.divide(BigDecimal.valueOf(months), currencyScale, RoundingMode.HALF_UP);
            }
            BigDecimal due = balance.add(interest).setScale(currencyScale, RoundingMode.HALF_UP);
            BigDecimal payment = regular.add(extra).min(due).setScale(currencyScale, RoundingMode.HALF_UP);
            // If cent rounding makes the planned payment fail to amortize, collect at least interest plus one minor unit.
            if (payment.compareTo(interest) <= 0 && balance.signum() > 0) payment = interest.add(unit).min(due);
            BigDecimal principalPaid = payment.subtract(interest).max(ZERO).min(balance).setScale(currencyScale, RoundingMode.HALF_UP);
            BigDecimal nextBalance = balance.subtract(principalPaid).max(ZERO).setScale(currencyScale, RoundingMode.HALF_UP);
            if (nextBalance.signum() > 0 && i == guard) {
                throw new IllegalArgumentException("The selected inputs do not repay within the supported horizon");
            }
            rows.add(new PaymentRow(i, date.plusMonths(i - 1L), payment, principalPaid, interest, nextBalance));
            interestTotal = interestTotal.add(interest);
            paymentTotal = paymentTotal.add(payment);
            balance = nextBalance;
        }
        if (balance.signum() > 0) throw new IllegalArgumentException("The loan could not be fully amortized");
        BigDecimal monthlyCostTotal = otherMonthly.multiply(BigDecimal.valueOf(rows.size()));
        return new Result(p.setScale(currencyScale, RoundingMode.HALF_UP), basePayment,
                paymentTotal, interestTotal, fee, monthlyCostTotal,
                paymentTotal.add(fee).add(monthlyCostTotal), rows);
    }

    private static void validate(BigDecimal principal, BigDecimal annualRate, int months) {
        if (principal == null || annualRate == null) throw new IllegalArgumentException("Required values are missing");
        checkedAmount(principal, "principal"); checkDecimal(annualRate, "annual rate");
        if (principal.signum() < 0 || annualRate.signum() < 0) throw new IllegalArgumentException("Amounts and rates cannot be negative");
        if (months < 1 || months > 420) throw new IllegalArgumentException("Term must be between 1 and 420 months");
        if (annualRate.compareTo(new BigDecimal("1000")) > 0) throw new IllegalArgumentException("Annual rate is too large");
    }

    private static BigDecimal checkedAmount(BigDecimal value, String name) {
        checkDecimal(value, name);
        if (value.signum() < 0 || value.compareTo(new BigDecimal("1000000000000000000")) > 0)
            throw new IllegalArgumentException(name + " must be between zero and 10^18");
        return value;
    }
    private static BigDecimal orZero(BigDecimal value) { return value == null ? ZERO : value; }
    private static void checkDecimal(BigDecimal value, String name) {
        if (value == null || value.precision() > 24 || value.scale() > 12 || value.scale() < -12)
            throw new IllegalArgumentException(name + " precision is outside the supported range");
    }
    private static void validateScale(int scale) {
        if (scale < 0 || scale > 4) throw new IllegalArgumentException("Invalid currency scale");
    }
}
