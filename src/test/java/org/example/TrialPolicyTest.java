package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.vendor.trial.TrialPolicy;
import ua.vendor.trial.Vendor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrialPolicyTest {

    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    private TrialPolicy createPolicy(Clock clock) {
        int buildNumber = Integer.getInteger("build", 1);
        return Vendor.build(buildNumber, clock);
    }

    private Clock kyivNoonClock(LocalDate date) {
        Instant noon = date.atTime(12, 0).atZone(KYIV).toInstant();
        return Clock.fixed(noon, KYIV);
    }


    @ParameterizedTest
    @CsvSource({
            "2025-05-01, 2025-05-01, 0",
            "2025-05-01, 2025-05-04, 0",
            "2025-05-01, 2025-05-07, 0",
            "2025-05-01, 2025-05-08, 199",
            "2025-05-01, 2025-05-20, 199",
            "2025-01-28, 2025-02-03, 0",
            "2025-01-28, 2025-02-04, 199"
    })
    void chargeAmountWithoutCancellationOnBoundaries(LocalDate registered, LocalDate today, int expectedCharge) {
        TrialPolicy policy = createPolicy(kyivNoonClock(today));

        int actual = policy.chargeAmount(registered, null);

        assertEquals(expectedCharge, actual);
    }


    @Test
    void freeWhenCancelledOnRegistrationDay() {
        TrialPolicy policy = createPolicy(kyivNoonClock(LocalDate.of(2025, 5, 10)));

        int actual = policy.chargeAmount(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 1));

        assertEquals(0, actual);
    }

    @Test
    void freeWhenCancelledOnSeventhDayInclusive() {
        TrialPolicy policy = createPolicy(kyivNoonClock(LocalDate.of(2025, 5, 10)));

        int actual = policy.chargeAmount(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 7));

        assertEquals(0, actual);
    }

    @Test
    void chargedWhenCancelledOnEighthDayAfterTrial() {
        TrialPolicy policy = createPolicy(kyivNoonClock(LocalDate.of(2025, 5, 10)));

        int actual = policy.chargeAmount(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 8));

        assertEquals(199, actual);
    }


    @Test
    void usesKyivDateWhenUtcIsStillSeventhDay() {
        // 2025-05-07 22:30 UTC -> у Києві (UTC+3) вже 2025-05-08 01:30 (8-й день -> 199 грн)
        Clock utcClock = Clock.fixed(Instant.parse("2025-05-07T22:30:00Z"), ZoneOffset.UTC);
        TrialPolicy policy = createPolicy(utcClock);

        int actual = policy.chargeAmount(LocalDate.of(2025, 5, 1), null);

        assertEquals(199, actual);
    }

    @Test
    void usesKyivDateWhenClockZoneIsAheadOfKyiv() {
        // 2025-05-07 20:30 UTC -> у Токіо (UTC+9) вже 2025-05-08 05:30, але в Києві ще 2025-05-07 23:30 (7-й день -> 0 грн)
        Clock tokyoClock = Clock.fixed(Instant.parse("2025-05-07T20:30:00Z"), ZoneId.of("Asia/Tokyo"));
        TrialPolicy policy = createPolicy(tokyoClock);

        int actual = policy.chargeAmount(LocalDate.of(2025, 5, 1), null);

        assertEquals(0, actual);
    }


    @Test
    void throwsWhenRegisteredDateIsNull() {
        TrialPolicy policy = createPolicy(kyivNoonClock(LocalDate.of(2025, 5, 10)));

        assertThrows(IllegalArgumentException.class,
                () -> policy.chargeAmount(null, null));
    }

    @Test
    void throwsWhenRegisteredDateIsInFuture() {
        TrialPolicy policy = createPolicy(kyivNoonClock(LocalDate.of(2025, 5, 10)));

        assertThrows(IllegalArgumentException.class,
                () -> policy.chargeAmount(LocalDate.of(2025, 5, 11), null));
    }

    @Test
    void throwsWhenCancelledDateIsBeforeRegistered() {
        TrialPolicy policy = createPolicy(kyivNoonClock(LocalDate.of(2025, 5, 10)));

        assertThrows(IllegalArgumentException.class,
                () -> policy.chargeAmount(LocalDate.of(2025, 5, 5), LocalDate.of(2025, 5, 4)));
    }
}