package com.sourceforgery.swedbank

import java.util.ArrayList

class PastTransaction private constructor(index: Int, result: Map<String, String>) : UrlEncodedData(index, result) {
    val authCode: String
    val merchantCity: String
    val merchantCountry: String
    val merchantName: String
    val microRefNumber: String
    val status: String
    val transactionDate: String
    val avv: String
    val cpnType: String
    val currency: Int
    val expiryDate: String
    val issueDate: String
    val numUsage: String
    val pan: String
    val validFrom: String
    val validTo: String
    val originalAmount: String
    val transactionAmount: String
    val transactionLimit: String
    val cumulativeLimit: String


    init {
        this.authCode = getString("AuthCode")
        this.merchantCity = getString("MerchantCity")
        this.merchantCountry = getString("MerchantCountry")
        this.merchantName = getString("MerchantName")
        this.microRefNumber = getString("MicroRefNumber")
        this.status = getString("Status")
        this.transactionDate = getString("TransactionDate")
        this.avv = getString("AVV")
        this.cpnType = getString("CPNType")
        this.currency = getInt("Currency")
        this.expiryDate = getString("ExpiryDate")
        this.issueDate = getString("IssueDate")
        this.numUsage = getString("NumUsage")
        this.pan = getString("PAN")
        this.validFrom = getString("ValidFrom")
        this.validTo = getString("ValidTo")
        this.originalAmount = getString("OriginalAmount")
        this.transactionAmount = getString("TransactionAmount")
        this.transactionLimit = getString("UTransactionLimit")
        this.cumulativeLimit = getString("UCumulativeLimit")

    }

    companion object {

        fun from(result: Map<String, String>): List<PastTransaction> {
            val pastTransactions = ArrayList<PastTransaction>()
            val total = UrlEncodedData.getTotal(result)
            for (i in 1..total) {
                pastTransactions.add(PastTransaction(i, result))
            }
            return pastTransactions
        }
    }
}
