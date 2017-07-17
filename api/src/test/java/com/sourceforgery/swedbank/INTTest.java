package com.sourceforgery.swedbank;

import java.io.IOException;
import java.util.List;

import com.sourceforgery.swedbank.ECardClient.Account;
import com.sourceforgery.swedbank.ECardClient.ECardAPI;

import okhttp3.logging.HttpLoggingInterceptor.Level;

public class INTTest {

    public void testLoginAndGetCards() throws Exception {
        ECardClient.setDebugLevel(Level.HEADERS);

        List<Account> accounts = ECardClient.login(System.getenv("PERSONNR"));
        ECardAPI api = accounts.get(1).selectIssuer();
        RealCard realCard = api.getCards().get(0);
        List<ActiveECard> activeECards = api.getActiveECards(realCard, 0);
        System.err.println(activeECards);
        api.getPastTransactions(realCard, 0);
        System.err.println(activeECards);
    }
}
