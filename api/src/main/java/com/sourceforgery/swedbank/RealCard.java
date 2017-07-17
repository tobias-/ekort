package com.sourceforgery.swedbank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RealCard extends UrlEncodedData {
    public final int adFrequency;
    public final int cardType;
    public final String cardholderName;
    public final boolean defaultCard;
    public final String nickname;
    public final int pan;
    public final int vCardId;
    public final boolean cpnService;

    private RealCard(final Map<String, String> result, final int index) {
        super(index, result);
        this.adFrequency = getInt("AdFrequency");
        this.cardType = getInt("CardType");
        this.cardholderName = getString("CardholderName");
        this.defaultCard = getBoolean("DefaultCard");
        this.nickname = getString("Nickname");
        this.pan = getInt("PAN");
        this.vCardId = getInt("VCardId");
        this.cpnService = getBoolean("CPN_Service");
    }

    public static List<RealCard> from(Map<String, String> map) {
        List<RealCard> realCards = new ArrayList<>();
        int totalCards = getTotal(map);
        for (int i = 1; i <= totalCards; i++) {
            realCards.add(new RealCard(map, i));
        }
        return realCards;
    }

}
