package com.sourceforgery.swedbank

class RealCard private constructor(result: Map<String, String>, index: Int) : UrlEncodedData(index, result) {
    val adFrequency: Int
    val cardType: Int
    val cardholderName: String
    val defaultCard: Boolean
    val nickname: String
    val pan: Int
    val vCardId: Int
    val cpnService: Boolean

    init {
        this.adFrequency = getInt("AdFrequency")
        this.cardType = getInt("CardType")
        this.cardholderName = getString("CardholderName")
        this.defaultCard = getBoolean("DefaultCard")
        this.nickname = getString("Nickname")
        this.pan = getInt("PAN")
        this.vCardId = getInt("VCardId")
        this.cpnService = getBoolean("CPN_Service")
    }

    companion object {

        fun from(map: Map<String, String>): List<RealCard> {
            val realCards = ArrayList<RealCard>()
            val totalCards = UrlEncodedData.Companion.getTotal(map)
            for (i in 1..totalCards) {
                realCards.add(RealCard(map, i))
            }
            return realCards
        }
    }

}
