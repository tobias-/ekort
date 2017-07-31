package com.sourceforgery.swedbank

import okhttp3.logging.HttpLoggingInterceptor.Level
import org.junit.Test

class INTTest {

    @Throws(Exception::class)
    @Test
    fun testLoginAndGetCards() {
        debugLevel = Level.BODY

        val eCardClient = ECardClient(System.getenv("PERSONNR"))
        eCardClient.loginWithoutPoll()
        val accounts = eCardClient.pollAndGetAccounts()
        val api = accounts[1].selectIssuer()
        val activeECards = api.getActiveECards(0)
        System.err.println(activeECards)
        api.getPastTransactions(0)
        System.err.println(activeECards)
    }
}
