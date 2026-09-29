package com.webapp.bankingportal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Currency;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.webapp.bankingportal.exception.ExchangeRateUnavailableException;
import com.webapp.bankingportal.exchange.CurrencyConversionService;
import com.webapp.bankingportal.exchange.ExchangeRateProvider;
import com.webapp.bankingportal.exchange.StubExchangeRateProvider;

import lombok.val;

public class CurrencyConversionServiceTests {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency JPY = Currency.getInstance("JPY");
    private static final Currency INR = Currency.getInstance("INR");

    private final CurrencyConversionService service = new CurrencyConversionService(new StubExchangeRateProvider());

    @Test
    public void test_same_currency_does_not_call_provider() {
        val provider = mock(ExchangeRateProvider.class);
        val conversion = new CurrencyConversionService(provider).convert(new BigDecimal("200"), USD, USD);

        verifyNoInteractions(provider);
        Assertions.assertEquals(new BigDecimal("200.00"), conversion.targetAmount());
        Assertions.assertEquals(0, BigDecimal.ONE.compareTo(conversion.exchangeRate()));
    }

    @Test
    public void test_convert_usd_to_eur() {
        val conversion = service.convert(new BigDecimal("200"), USD, EUR);

        Assertions.assertEquals(USD, conversion.sourceCurrency());
        Assertions.assertEquals(new BigDecimal("200"), conversion.sourceAmount());
        Assertions.assertEquals(EUR, conversion.targetCurrency());
        Assertions.assertEquals(new BigDecimal("184.00"), conversion.targetAmount());
        Assertions.assertEquals(0, new BigDecimal("0.92").compareTo(conversion.exchangeRate()));
    }

    @Test
    public void test_target_amount_rounded_to_target_currency_minor_units() {
        Assertions.assertEquals(new BigDecimal("217.39"), service.convert(new BigDecimal("200"), EUR, USD).targetAmount());
        Assertions.assertEquals(new BigDecimal("1803"), service.convert(new BigDecimal("1000"), INR, JPY).targetAmount());
    }

    @Test
    public void test_rounding_is_half_even() {
        val provider = mock(ExchangeRateProvider.class);
        when(provider.getRate(any(), any())).thenReturn(new BigDecimal("0.00125"));
        val converter = new CurrencyConversionService(provider);

        Assertions.assertEquals(new BigDecimal("0.12"), converter.convert(new BigDecimal("100"), USD, EUR).targetAmount());
        Assertions.assertEquals(new BigDecimal("0.38"), converter.convert(new BigDecimal("300"), USD, EUR).targetAmount());
    }

    @Test
    public void test_provider_failure_propagates() {
        val provider = mock(ExchangeRateProvider.class);
        when(provider.getRate(any(), any())).thenThrow(new ExchangeRateUnavailableException("down"));

        Assertions.assertThrows(ExchangeRateUnavailableException.class,
                () -> new CurrencyConversionService(provider).convert(BigDecimal.TEN, USD, EUR));
    }
}
