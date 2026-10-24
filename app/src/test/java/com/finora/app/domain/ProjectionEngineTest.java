package com.finora.app.domain;

import org.junit.Test;
import java.math.BigDecimal;
import static org.junit.Assert.*;

public class ProjectionEngineTest {
    private static BigDecimal bd(String value) { return new BigDecimal(value); }

    @Test public void regularDepositsAccumulateAtZeroReturn() {
        ProjectionEngine.Result result = ProjectionEngine.savings(bd("100"), bd("10"), bd("0"), 1, 2);
        assertEquals("220.00", result.endingBalance.toPlainString());
        assertEquals("220.00", result.contributions.toPlainString());
        assertEquals("0.00", result.growth.toPlainString());
    }

    @Test public void compoundingProducesGrowthAboveContributions() {
        ProjectionEngine.Result result = ProjectionEngine.savings(bd("1000"), bd("100"), bd("6"), 10, 2);
        assertTrue(result.endingBalance.compareTo(result.contributions) > 0);
        assertEquals(11, result.yearlyBalances.size());
    }

    @Test public void zeroInflationPreservesPurchasingPower() {
        assertEquals("5000.00", ProjectionEngine.purchasingPower(bd("5000"), bd("0"), 20, 2).toPlainString());
    }

    @Test public void inflationAndDeflationChangeRealValue() {
        BigDecimal inflated = ProjectionEngine.purchasingPower(bd("1000"), bd("3"), 10, 2);
        BigDecimal deflated = ProjectionEngine.purchasingPower(bd("1000"), bd("-2"), 10, 2);
        assertTrue(inflated.compareTo(bd("1000")) < 0);
        assertTrue(deflated.compareTo(bd("1000")) > 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInflationAtOrBelowMinusOneHundredPercent() {
        ProjectionEngine.purchasingPower(bd("100"), bd("-100"), 10, 2);
    }
}
