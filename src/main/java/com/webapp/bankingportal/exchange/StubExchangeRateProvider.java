package com.webapp.bankingportal.exchange;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Currency;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.webapp.bankingportal.exception.ExchangeRateUnavailableException;
import com.webapp.bankingportal.util.ApiMessages;

/**
 * Fixed-rate provider for development and testing. Rates are expressed as units
 * of each currency per 1 USD; cross rates are derived through USD.
 */
@Component
@ConditionalOnProperty(name = "exchange.provider", havingValue = "stub", matchIfMissing = true)
public class StubExchangeRateProvider implements ExchangeRateProvider {

    static final Map<String, BigDecimal> DEFAULT_USD_RATES = Map.of(
            "USD", new BigDecimal("1"),
            "EUR", new BigDecimal("0.92"),
            "GBP", new BigDecimal("0.79"),
            "INR", new BigDecimal("83.20"),
            "JPY", new BigDecimal("150.00"),
            "CAD", new BigDecimal("1.36"),
            "AUD", new BigDecimal("1.52"));

    private final Map<String, BigDecimal> usdRates;

    public StubExchangeRateProvider() {
        this(DEFAULT_USD_RATES);
    }

    public StubExchangeRateProvider(Map<String, BigDecimal> usdRates) {
        this.usdRates = Map.copyOf(usdRates);
    }

    @Override
    public BigDecimal getRate(Currency source, Currency target) {
        if (source.equals(target)) {
            return BigDecimal.ONE;
        }

        return usdRate(target).divide(usdRate(source), MathContext.DECIMAL64);
    }

    private BigDecimal usdRate(Currency currency) {
        final BigDecimal rate = usdRates.get(currency.getCurrencyCode());
        if (rate == null) {
            throw new ExchangeRateUnavailableException(
                    String.format(ApiMessages.EXCHANGE_RATE_UNAVAILABLE.getMessage(), currency.getCurrencyCode()));
        }

        return rate;
    }
}
