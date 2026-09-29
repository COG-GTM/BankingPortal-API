package com.webapp.bankingportal.exchange;

import java.math.BigDecimal;
import java.util.Currency;

public record CurrencyConversion(
        Currency sourceCurrency,
        BigDecimal sourceAmount,
        Currency targetCurrency,
        BigDecimal targetAmount,
        BigDecimal exchangeRate) {
}
