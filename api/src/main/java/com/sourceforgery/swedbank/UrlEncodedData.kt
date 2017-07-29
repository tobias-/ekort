package com.sourceforgery.swedbank

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.ChronoField.*
import java.util.*

open class UrlEncodedData internal constructor(private val index: Int, private val data: Map<String, String>) {

    internal fun getInt(fieldName: String): Int {
        return getString(fieldName).toInt()
    }

    internal fun getString(fieldName: String): String {
        return data[fieldName + index] ?:  throw IllegalArgumentException("No ${fieldName} in object data")
    }

    internal fun getBoolean(fieldName: String): Boolean {
        val value = getString(fieldName)
        return "Y" == value || "true" == value
    }

    internal fun getCurrency(fieldName: String): Currency {
        val currencyCode = getInt(fieldName)
        return Currency.getAvailableCurrencies()
                .stream()
                .filter { c -> c.numericCode == currencyCode }
                .findFirst()
                .orElseThrow { IllegalArgumentException("No currency with id $currencyCode is known") }
    }

    fun getLocalDate(fieldName: String): LocalDate {
        return LocalDate.parse(getString(fieldName), LOCAL_DATE)
    }

    fun getBigDecimal(fieldName: String): BigDecimal {
        val str = getString(fieldName)
        val matcher = FIND_AMOUNT.find(str) ?: throw IllegalArgumentException("There's no amount in " + str)
        val number = matcher.groupValues[1].replace(".", "").replace(',', '.')
        return BigDecimal(number)
    }

    companion object {
        private val LOCAL_DATE = DateTimeFormatterBuilder().appendValue(DAY_OF_MONTH, 2, 2, SignStyle.EXCEEDS_PAD)
                .appendLiteral('/')
                .appendValue(MONTH_OF_YEAR, 2)
                .appendLiteral('/')
                .appendValue(YEAR, 2)
                .toFormatter()
        private val FIND_AMOUNT = Regex("([0-9].*[0-9])")

        internal fun getTotal(map: Map<String, String>): Int {
            return Integer.parseInt(map["Total"])
        }
    }
}
