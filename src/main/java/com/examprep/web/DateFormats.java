package com.examprep.web;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

/** Small formatting helpers used by Thymeleaf views. */
@Component("dates")
public class DateFormats {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    public String day(Instant instant) {
        return instant == null ? "—" : DAY.format(instant.atZone(ZoneId.systemDefault()));
    }

    public String dayTime(Instant instant) {
        return instant == null ? "—" : DAY_TIME.format(instant.atZone(ZoneId.systemDefault()));
    }

    public String day(LocalDate date) {
        return date == null ? "not set" : DAY.format(date);
    }

    public String minutes(int seconds) {
        if (seconds <= 0) {
            return "0m";
        }
        Duration duration = Duration.ofSeconds(seconds);
        long minutes = duration.toMinutes();
        long remaining = duration.toSecondsPart();
        return remaining == 0 ? minutes + "m" : minutes + "m " + remaining + "s";
    }
}
