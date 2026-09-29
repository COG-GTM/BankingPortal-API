package com.webapp.bankingportal.exchange;

import java.math.BigDecimal;
import java.util.Currency;

import com.webapp.bankingportal.exception.ExchangeRateUnavailableException;

/**
 * Source of foreign-exchange rates. Implementations return how many units of
 * {@code target} one unit of {@code source} buys.
 */
public interface ExchangeRateProvider {

    BigDecimal getRate(Currency source, Currency target) throws ExchangeRateUnavailableException;
}
