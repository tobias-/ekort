package com.sourceforgery.swedbank

import java.math.BigDecimal
import java.time.LocalDate
import java.util.ArrayList
import java.util.Currency

class ActiveECard internal constructor(index: Int, data: Map<String, String>) : UrlEncodedData(index, data) {
    val merchantId: String
    val merchantName: String
    val cvv: String
    val cpnType: String
    val currency: Currency
    val expiry: String
    val issueDate: LocalDate
    val numUsages: Int
    val creditCardNumber: String
    val validFrom: LocalDate
    val validTo: LocalDate
    val transactionLimit: BigDecimal
    val cumulativeLimit: BigDecimal
    val authAmount: BigDecimal
    val openToBuy: BigDecimal

    init {
        merchantId = getString("MerchantId")
        merchantName = getString("MerchantName")
        cvv = getString("avv")
        cpnType = getString("CPNType")
        currency = getCurrency("Currency")
        expiry = getString("Expiry")
        issueDate = getLocalDate("IssueDate")
        numUsages = getInt("NumUsage")
        creditCardNumber = getString("PAN")
        validFrom = getLocalDate("ValidFrom")
        validTo = getLocalDate("ValidTo")
        transactionLimit = getBigDecimal("UTransactionLimit")
        cumulativeLimit = getBigDecimal("UCumulativeLimit")
        authAmount = getBigDecimal("AuthAmount")
        openToBuy = getBigDecimal("UOpenToBuy")
    }

    val isUnused: Boolean
        get() = "-" == merchantId && numUsages == 0

    override fun toString(): String {
        return "ActiveECard{" +
                "merchantId='" + merchantId + '\'' +
                ", merchantName='" + merchantName + '\'' +
                ", cvv='" + cvv + '\'' +
                ", cpnType='" + cpnType + '\'' +
                ", currency=" + currency +
                ", expiry='" + expiry + '\'' +
                ", issueDate=" + issueDate +
                ", numUsages=" + numUsages +
                ", creditCardNumber='" + creditCardNumber + '\'' +
                ", validFrom=" + validFrom +
                ", validTo=" + validTo +
                ", transactionLimit=" + transactionLimit +
                ", cumulativeLimit=" + cumulativeLimit +
                ", authAmount=" + authAmount +
                ", openToBuy=" + openToBuy +
                "} " + super.toString()
    }

    companion object {

        fun from(data: Map<String, String>): List<ActiveECard> {
            val total = UrlEncodedData.getTotal(data)
            val activeECards = ArrayList<ActiveECard>()
            for (i in 1..total) {
                activeECards.add(ActiveECard(i, data))
            }
            return activeECards
        }
    }
}
