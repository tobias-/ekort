package com.sourceforgery.swedbank;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.util.Currency;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlEncodedData {
    private static final DateTimeFormatter LOCAL_DATE = new DateTimeFormatterBuilder().appendValue(DAY_OF_MONTH, 2, 2, SignStyle.EXCEEDS_PAD)
                                                                                      .appendLiteral('/')
                                                                                      .appendValue(MONTH_OF_YEAR, 2)
                                                                                      .appendLiteral('/')
                                                                                      .appendValue(YEAR, 2)
                                                                                      .toFormatter();
    private static final Pattern FIND_AMOUNT = Pattern.compile("([0-9].*[0-9])");


    final int index;
    private final Map<String, String> data;


    UrlEncodedData(final int index, Map<String, String> data) {
        this.index = index;
        this.data = data;
    }

    int getInt(String fieldName) {
        return Integer.parseInt(getString(fieldName));
    }

    String getString(String fieldName) {
        return data.get(fieldName + index);
    }

    boolean getBoolean(String fieldName) {
        String value = getString(fieldName);
        return "Y".equals(value) || "true".equals(value);
    }

    Currency getCurrency(String fieldName) {
        int currencyCode = getInt(fieldName);
        return Currency.getAvailableCurrencies()
                       .stream()
                       .filter(c -> c.getNumericCode() == currencyCode)
                       .findFirst()
                       .orElseThrow(() -> new IllegalArgumentException("No currency with id " + currencyCode + " is known"));
    }

    static int getTotal(final Map<String, String> map) {
        return Integer.parseInt(map.get("Total"));
    }

    public LocalDate getLocalDate(final String fieldName) {
        return LocalDate.parse(getString(fieldName), LOCAL_DATE);
    }

    public BigDecimal getBigDecimal(final String fieldName) {
        String str = getString(fieldName);
        Matcher matcher = FIND_AMOUNT.matcher(str);
        if (!matcher.find()) {
            throw new IllegalArgumentException("There's no amount in " + str);
        }
        String number = matcher.group(1).replace(".", "").replace(',', '.');
        return new BigDecimal(number);
    }
}
