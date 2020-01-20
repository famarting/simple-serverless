package io.famargon.k8s;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

public class GracePeriodParsingTest {

    @Test
    void testGracePeriodParsing() {
        String duration = "30s";

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

        long totalSeconds = 0;

        if (unit.equals("s")) {
            totalSeconds = Integer.parseInt(digit);
        } else if (unit.equals("ms")){
            totalSeconds = Integer.parseInt(digit) / 1000;
        } else {
            throw new IllegalArgumentException("Invalid value, unsupported unit");
        }

        assertTrue(totalSeconds==30);
    }

}
