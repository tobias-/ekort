package com.sourceforgery.swedbank

data class ActiveECard internal constructor(val index: Int, val data: Map<String, String>) : UrlEncodedData(index, data) {
    val merchantId = getString("MerchantId")
    val merchantName = getString("MerchantName")
    val cvv = getString("avv")
    val cpnType = getString("CPNType")
    val currency = getInt("Currency")
    val expiry = getString("Expiry")
    val issueDate = getLocalDate("IssueDate")
    val numUsages = getInt("NumUsage")
    val creditCardNumber = getString("PAN")
    val validFrom = getLocalDate("ValidFrom")
    val validTo = getLocalDate("ValidTo")
    val transactionLimit = getBigDecimal("UTransactionLimit")
    val cumulativeLimit = getBigDecimal("UCumulativeLimit")
    val authAmount = getBigDecimal("AuthAmount")
    val openToBuy = getBigDecimal("UOpenToBuy")

    val isUnused: Boolean
        get() = "-" == merchantId && numUsages == 0

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
