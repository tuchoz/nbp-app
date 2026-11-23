package pl.nbp.app.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;

public class CachePolicy {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    public static Duration ttl(ZonedDateTime now) {
        ZonedDateTime warsawTime = now.withZoneSameInstant(WARSAW);

        DayOfWeek day = warsawTime.getDayOfWeek();
        LocalTime time = warsawTime.toLocalTime();

        boolean weekday = day != DayOfWeek.SATURDAY
                && day != DayOfWeek.SUNDAY;

        boolean publicationWindow =
                !time.isBefore(LocalTime.of(11, 45))
                && time.isBefore(LocalTime.of(12, 30));

        return weekday && publicationWindow
                ? Duration.ofMinutes(1)
                : Duration.ofMinutes(15);
    }
}