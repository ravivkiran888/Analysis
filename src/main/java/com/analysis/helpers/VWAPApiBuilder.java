package com.analysis.helpers;

import java.util.List;

import com.analysis.requests.VWAPRequest;
import com.analysis.util.DateUtil;

public class VWAPApiBuilder {

    private static final String BASE_URL   = "https://openapi.5paisa.com";
    private static final String EXCH       = "N";
    private static final String EXCH_TYPE  = "C";
    private static final String INTERVAL   = "5m";

   
    /**
     * Build requests for a single scrip (used by scheduler)
     */
    public static List<VWAPRequest> buildRequests(int scripCode) {

        String today = DateUtil.today();
        return List.of(buildSingleRequest(scripCode, today));
    }

    /**
     * Internal reusable builder
     */
    private static VWAPRequest buildSingleRequest(int scripCode, String date) {

        String url = String.format(
            "%s/V2/historical/%s/%s/%d/%s?from=%s&end=%s",
            BASE_URL,
            EXCH,
            EXCH_TYPE,
            scripCode,
            INTERVAL,
            date,
            date
        );

        return new VWAPRequest(scripCode, url);
    }
}
