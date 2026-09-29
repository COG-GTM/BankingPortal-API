package com.webapp.bankingportal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Currency;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.webapp.bankingportal.exception.ExchangeRateUnavailableException;
import com.webapp.bankingportal.exchange.StubExchangeRateProvider;

import lombok.val;

public class StubExchangeRateProviderTests {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency CHF = Currency.getInstance("CHF");

    private final StubExchangeRateProvider provider = new StubExchangeRateProvider();

    @Test
    public void test_same_currency_rate_is_one() {
        Assertions.assertEquals(0, BigDecimal.ONE.compareTo(provider.getRate(EUR, EUR)));
    }

    @Test
    public void test_rate_from_usd_uses_configured_rate() {
        Assertions.assertEquals(0, new BigDecimal("0.92").compareTo(provider.getRate(USD, EUR)));
    }

    @Test
    public void test_cross_rate_is_derived_through_usd() {
        val expected = new BigDecimal("0.79").divide(new BigDecimal("0.92"), MathContext.DECIMAL64);
        Assertions.assertEquals(0, expected.compareTo(provider.getRate(EUR, GBP)));
    }

    @Test
    public void test_inverse_rates_multiply_to_one() {
        val product = provider.getRate(USD, GBP).multiply(provider.getRate(GBP, USD));
        Assertions.assertEquals(1.0, product.doubleValue(), 1e-12);
    }

    @Test
    public void test_unsupported_currency_throws() {
        Assertions.assertThrows(ExchangeRateUnavailableException.class, () -> provider.getRate(USD, CHF));
        Assertions.assertThrows(ExchangeRateUnavailableException.class, () -> provider.getRate(CHF, USD));
    }

    @Test
    public void test_custom_rate_table() {
        val custom = new StubExchangeRateProvider(Map.of("USD", BigDecimal.ONE, "CHF", new BigDecimal("0.88")));
        Assertions.assertEquals(0, new BigDecimal("0.88").compareTo(custom.getRate(USD, CHF)));
        Assertions.assertThrows(ExchangeRateUnavailableException.class, () -> custom.getRate(USD, EUR));
    }
}
