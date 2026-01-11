package com.analysis.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.analysis.APPConstants;

public class DateUtil {

    public static String today() {
        return LocalDate.now()
                .format(DateTimeFormatter.ofPattern(APPConstants.DATE_FORMAT_YYYY));
    }
}
