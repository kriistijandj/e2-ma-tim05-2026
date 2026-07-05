package com.example.slagalica.helper;

import java.util.Calendar;
import java.util.Locale;

public class DateHelper {
    public static String getCurrentWeeklyCycleId() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.YEAR) + "_W" + cal.get(Calendar.WEEK_OF_YEAR);
    }

    public static String getCurrentMonthlyCycleId() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.YEAR) + "_M" + String.format(Locale.getDefault(), "%02d", cal.get(Calendar.MONTH) + 1);
    }
}