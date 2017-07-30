package com.sourceforgery.swedbank

class RealCard private constructor(result: Map<String, String>, index: Int) : UrlEncodedData(index, result) {
    val adFrequency= getInt("AdFrequency")
    val cardType= getInt("CardType")
    val cardholderName= getString("CardholderName")
    val defaultCard= getBoolean("DefaultCard")
    val nickname= getString("Nickname")
    val pan= getInt("PAN")
    val vCardId= getInt("VCardId")
    val cpnService= getBoolean("CPN_Service")

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
