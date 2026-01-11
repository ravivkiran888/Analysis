package com.analysis.helpers;

import java.util.ArrayList;
import java.util.List;

import com.analysis.APPConstants;
import com.analysis.requests.VWAPRequest;
import com.analysis.util.DateUtil;

public class VWAPApiBuilder {

    private static final String BASE_URL = APPConstants.BASE_URL;
    private static final String EXCH = APPConstants.EXCH;
    private static final String EXCH_TYPE = APPConstants.EXCH_TYPE;
    private static final String INTERVAL = "5m";

    public static List<VWAPRequest> buildRequests(List<Integer> scripCodes) {

        String today = DateUtil.today();
        List<VWAPRequest> requests = new ArrayList<>();

        for (Integer scripCode : scripCodes) {

            String url = String.format(
                "%s/V2/historical/%s/%s/%d/%s?from=%s&end=%s",
                BASE_URL,
                EXCH,
                EXCH_TYPE,
                scripCode,
                INTERVAL,
                today,
                today
            );

            requests.add(new VWAPRequest(scripCode, url));
        }

        return requests;
    }

}
