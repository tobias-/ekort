package com.sourceforgery.swedbank

import okhttp3.logging.HttpLoggingInterceptor.Level

class INTTest {

    @Throws(Exception::class)
    fun testLoginAndGetCards() {
        ECardClient.debugLevel = Level.HEADERS

        val accounts = ECardClient.login(System.getenv("PERSONNR"))
        val api = accounts[1].selectIssuer()
        val realCard = api.getCards()[0]
        val activeECards = api.getActiveECards(realCard, 0)
        System.err.println(activeECards)
        api.getPastTransactions(realCard, 0)
        System.err.println(activeECards)
    }
}
