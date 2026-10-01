package co.ke.bremac.posapp.util;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class MoneyTest {
    @Test public void formatsWithBusinessSeparators() {
        Money money = new Money("KES", "KSh", ",", ".", 2);
        assertEquals("KSh 1,234.50", money.format(1234.5));
        assertEquals("-KSh 1,234.57", money.format(-1234.565));
    }

    @Test public void roundsToCurrencyPrecision() {
        Money money = new Money("JPY", "¥", ",", ".", 0);
        assertEquals(new BigDecimal("11"), money.round(new BigDecimal("10.5")));
    }
}
