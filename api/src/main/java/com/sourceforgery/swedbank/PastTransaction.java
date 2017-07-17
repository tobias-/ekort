package com.sourceforgery.swedbank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PastTransaction extends UrlEncodedData {
    public final String authCode;
    public final String merchantCity;
    public final String merchantCountry;
    public final String merchantName;
    public final String microRefNumber;
    public final String status;
    public final String transactionDate;
    public final String avv;
    public final String cpnType;
    public final int currency;
    public final String expiryDate;
    public final String issueDate;
    public final String numUsage;
    public final String pan;
    public final String validFrom;
    public final String validTo;
    public final String originalAmount;
    public final String transactionAmount;
    public final String transactionLimit;
    public final String cumulativeLimit;


    private PastTransaction(int index, Map<String, String> result) {
        super(index, result);
        this.authCode = getString("AuthCode");
        this.merchantCity = getString("MerchantCity");
        this.merchantCountry = getString("MerchantCountry");
        this.merchantName = getString("MerchantName");
        this.microRefNumber = getString("MicroRefNumber");
        this.status = getString("Status");
        this.transactionDate = getString("TransactionDate");
        this.avv = getString("AVV");
        this.cpnType = getString("CPNType");
        this.currency = getInt("Currency");
        this.expiryDate = getString("ExpiryDate");
        this.issueDate = getString("IssueDate");
        this.numUsage = getString("NumUsage");
        this.pan = getString("PAN");
        this.validFrom = getString("ValidFrom");
        this.validTo = getString("ValidTo");
        this.originalAmount = getString("OriginalAmount");
        this.transactionAmount = getString("TransactionAmount");
        this.transactionLimit = getString("UTransactionLimit");
        this.cumulativeLimit = getString("UCumulativeLimit");

    }

    public static List<PastTransaction> from(final Map<String, String> result) {
        List<PastTransaction> pastTransactions = new ArrayList<>();
        int total = getTotal(result);
        for (int i = 1; i <= total; i++) {
            pastTransactions.add(new PastTransaction(i, result));
        }
        return pastTransactions;
    }
}
