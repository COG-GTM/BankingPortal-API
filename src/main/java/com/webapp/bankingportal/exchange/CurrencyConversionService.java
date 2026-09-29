package com.webapp.bankingportal.exchange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CurrencyConversionService {

    private final ExchangeRateProvider exchangeRateProvider;

    public CurrencyConversion convert(BigDecimal amount, Currency source, Currency target) {
        final BigDecimal rate = source.equals(target)
                ? BigDecimal.ONE
                : exchangeRateProvider.getRate(source, target);

        final BigDecimal targetAmount = amount.multiply(rate)
                .setScale(Math.max(target.getDefaultFractionDigits(), 0), RoundingMode.HALF_EVEN);

        return new CurrencyConversion(source, amount, target, targetAmount, rate);
    }
}
