package com.sourceforgery.swedbank

import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormatterBuilder
import java.math.BigDecimal
import java.util.Currency

open class UrlEncodedData internal constructor(private val index: Int, private val data: Map<String, String>) {

    internal fun getInt(fieldName: String): Int {
        return getString(fieldName).toInt()
    }

    internal fun getString(fieldName: String): String {
        return data[fieldName + index] ?: throw IllegalArgumentException("No ${fieldName} in object data")
    }

    internal fun getBoolean(fieldName: String): Boolean {
        val value = getString(fieldName)
        return "Y" == value || "true" == value
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
        private val LOCAL_DATE = DateTimeFormatterBuilder()
                .appendDayOfMonth(2)
                .appendLiteral('/')
                .appendMonthOfYear(2)
                .appendLiteral('/')
                .appendYear(2, 2).toFormatter()

        private val FIND_AMOUNT = Regex("([0-9].*[0-9])")

        internal fun getTotal(map: Map<String, String>): Int {
            return Integer.parseInt(map["Total"])
        }
    }
}
