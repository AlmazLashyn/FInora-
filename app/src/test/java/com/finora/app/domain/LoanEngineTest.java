package com.finora.app.domain;

import org.junit.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class LoanEngineTest {
    private static BigDecimal bd(String value) { return new BigDecimal(value); }
    private LoanEngine.Result calculate(String principal, String rate, int months, LoanEngine.PaymentType type, String extra) {
        return LoanEngine.calculate(bd(principal), bd(rate), months, type, bd("0"), bd("0"), bd(extra), LocalDate.of(2026, 1, 1), 2);
    }

    @Test public void zeroRateAnnuityPaysPrincipalEvenly() {
        assertEquals("100.00", LoanEngine.annuityPayment(bd("1200"), bd("0"), 12, 2).toPlainString());
        LoanEngine.Result result = calculate("1200", "0", 12, LoanEngine.PaymentType.ANNUITY, "0");
        assertEquals("0.00", result.totalInterest.toPlainString());
        assertEquals("1200.00", result.totalPaid.toPlainString());
        assertEquals(12, result.actualMonths());
    }

    @Test public void lowRateAndLongTermAmortizeWithoutNegativeBalance() {
        LoanEngine.Result result = calculate("300000", "0.01", 420, LoanEngine.PaymentType.ANNUITY, "0");
        assertEquals(420, result.actualMonths());
        assertEquals(BigDecimal.ZERO.setScale(2), result.schedule.get(419).balance);
        assertTrue(result.totalPaid.compareTo(result.principal) >= 0);
    }

    @Test public void oneMonthAndHighRateCloseOnFinalPayment() {
        LoanEngine.Result result = calculate("1000", "120", 1, LoanEngine.PaymentType.ANNUITY, "0");
        assertEquals(1, result.actualMonths());
        assertEquals(BigDecimal.ZERO.setScale(2), result.schedule.get(0).balance);
        assertEquals("100.00", result.totalInterest.toPlainString());
        assertEquals("1100.00", result.totalPaid.toPlainString());
    }

    @Test public void differentiatedPrincipalSharesSumToOriginalAmount() {
        LoanEngine.Result result = calculate("1200", "12", 12, LoanEngine.PaymentType.DIFFERENTIATED, "0");
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal priorPayment = null;
        for (LoanEngine.PaymentRow row : result.schedule) {
            principal = principal.add(row.principal);
            if (priorPayment != null) assertTrue(row.payment.compareTo(priorPayment) <= 0);
            priorPayment = row.payment;
        }
        assertEquals("1200.00", principal.toPlainString());
    }

    @Test public void regularEarlyPaymentShortensTermAndNeverOverpays() {
        LoanEngine.Result regular = calculate("25000", "8.9", 60, LoanEngine.PaymentType.ANNUITY, "0");
        LoanEngine.Result early = calculate("25000", "8.9", 60, LoanEngine.PaymentType.ANNUITY, "100");
        assertTrue(early.actualMonths() < regular.actualMonths());
        assertTrue(early.totalInterest.compareTo(regular.totalInterest) < 0);
        assertEquals(BigDecimal.ZERO.setScale(2), early.schedule.get(early.actualMonths() - 1).balance);
        assertTrue(early.schedule.get(early.actualMonths() - 1).principal.compareTo(bd("25000")) <= 0);
    }

    @Test public void extraBeyondRemainingBalanceIsCapped() {
        LoanEngine.Result result = calculate("100", "0", 12, LoanEngine.PaymentType.ANNUITY, "100000");
        assertEquals(1, result.actualMonths());
        assertEquals("100.00", result.totalPaid.toPlainString());
    }

    @Test public void feesAndMonthlyExpensesAreSeparateFromInterest() {
        LoanEngine.Result result = LoanEngine.calculate(bd("1200"), bd("0"), 12, LoanEngine.PaymentType.ANNUITY,
                bd("25"), bd("5"), bd("0"), LocalDate.of(2026, 1, 1), 2);
        assertEquals("0.00", result.totalInterest.toPlainString());
        assertEquals("60.00", result.monthlyCosts.toPlainString());
        assertEquals("1285.00", result.totalCost.toPlainString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutOfRangeTerm() { calculate("1000", "5", 421, LoanEngine.PaymentType.ANNUITY, "0"); }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsExtremeMagnitudeBeforeRounding() {
        LoanEngine.calculate(bd("1E1000"), bd("5"), 12, LoanEngine.PaymentType.ANNUITY,
                bd("0"), bd("0"), bd("0"), LocalDate.of(2026, 1, 1), 2);
    }
}
