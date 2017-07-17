package com.sourceforgery.swedbank;

import java.util.Map;

public class CPN {
    private final String avv;
    private final String from;
    private final String pan;
    private final String expiryMonth;
    private final String expiryYear;

    private CPN(final Map<String, String> data) {
        avv = data.get("AVV");
        from = data.get("From");
        pan = data.get("PAN");
        expiryMonth = data.get("ExpiryMonth");
        expiryYear = data.get("ExpiryYear");
    }

    public static CPN from(final Map<String, String> data) {
        return new CPN(data);
    }
}
