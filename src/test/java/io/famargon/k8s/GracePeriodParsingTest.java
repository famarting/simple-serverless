package io.famargon.k8s;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class GracePeriodParsingTest {

    @Test
    void testGracePeriodParsing() {
        String duration = "30s";

        long totalSeconds = Utils.parseToSeconds(duration);

        assertTrue(totalSeconds==30);
    }

}
