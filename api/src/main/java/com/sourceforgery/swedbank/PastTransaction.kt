package com.sourceforgery.swedbank

data class PastTransaction constructor(val index: Int, val result: Map<String, String>) : UrlEncodedData(index, result) {
    val authCode = getString("AuthCode")
    val merchantCity = getString("MerchantCity")
    val merchantCountry = getString("MerchantCountry")
    val merchantName = getString("MerchantName")
    val microRefNumber = getString("MicroRefNumber")
    val status = getString("Status")
    val transactionDate = getString("TransactionDate")
    val avv = getString("AVV")
    val cpnType = getString("CPNType")
    val currency = getInt("Currency")
    val expiryDate = getString("ExpiryDate")
    val issueDate = getString("IssueDate")
    val numUsage = getString("NumUsage")
    val pan = getString("PAN")
    val validFrom = getString("ValidFrom")
    val validTo = getString("ValidTo")
    val originalAmount = getString("OriginalAmount")
    val transactionAmount = getString("TransactionAmount")
    val transactionLimit = getString("UTransactionLimit")
    val cumulativeLimit = getString("UCumulativeLimit")
    val prettyTransactionAmount = transactionAmount.replace(REGEX, "$1$3 $2").replace('.', ' ').replace(',', '.').replace("kr", "SEK")

    companion object {
        val REGEX = Regex("([^0-9,.]*)([0-9.,]*)([^0-9,.]*)")

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
