package com.sourceforgery.swedbank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;

public class ActiveECard extends UrlEncodedData {
    public final String merchantId;
    public final String merchantName;
    public final String cvv;
    public final String cpnType;
    public final Currency currency;
    public final String expiry;
    public final LocalDate issueDate;
    public final int numUsages;
    public final String creditCardNumber;
    public final LocalDate validFrom;
    public final LocalDate validTo;
    public final BigDecimal transactionLimit;
    public final BigDecimal cumulativeLimit;
    public final BigDecimal authAmount;
    public final BigDecimal openToBuy;

    ActiveECard(final int index, final Map<String, String> data) {
        super(index, data);
        merchantId = getString("MerchantId");
        merchantName = getString("MerchantName");
        cvv = getString("avv");
        cpnType = getString("CPNType");
        currency = getCurrency("Currency");
        expiry = getString("Expiry");
        issueDate = getLocalDate("IssueDate");
        numUsages = getInt("NumUsage");
        creditCardNumber = getString("PAN");
        validFrom = getLocalDate("ValidFrom");
        validTo = getLocalDate("ValidTo");
        transactionLimit = getBigDecimal("UTransactionLimit");
        cumulativeLimit = getBigDecimal("UCumulativeLimit");
        authAmount = getBigDecimal("AuthAmount");
        openToBuy = getBigDecimal("UOpenToBuy");
    }

    public static List<ActiveECard> from(final Map<String, String> data) {
        int total = getTotal(data);
        List<ActiveECard> activeECards = new ArrayList<>();
        for (int i = 1; i <= total; i++) {
            activeECards.add(new ActiveECard(i, data));
        }
        return activeECards;
    }

    public boolean isUnused() {
        return "-".equals(merchantId) && numUsages == 0;
    }

    @Override
    public String toString() {
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
                "} " + super.toString();
    }
}
