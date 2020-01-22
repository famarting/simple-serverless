package io.famargon.k8s;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    public static int parseToSeconds(String duration) {
        Matcher digitsMatcher = Pattern.compile("\\d+").matcher(duration);
        String digit;
        if (digitsMatcher.find()) {
            digit = digitsMatcher.group();
        } else {
            throw new IllegalArgumentException("Invalid value, no digits");
        }

        Matcher unitMatcher = Pattern.compile("[a-z]").matcher(duration);
        String unit;
        if (unitMatcher.find()) {
            unit = unitMatcher.group();
        } else {
            throw new IllegalArgumentException("Invalid value, no unit");
        }

        int totalSeconds = 0;

        if (unit.equals("s")) {
            totalSeconds = Integer.parseInt(digit);
        } else if (unit.equals("ms")){
            totalSeconds = Integer.parseInt(digit) / 1000;
        } else {
            throw new IllegalArgumentException("Invalid value, unsupported unit");
        }

        return totalSeconds;
    }

    public static <T> List<T> tail(List<T> list, int lenght) {
        int size = list.size();
        int fromIndex;
        if (size < lenght) {
            fromIndex = 0;
        } else {
            fromIndex = size - lenght;
        }
        return list.subList(fromIndex, size);
    }

}
