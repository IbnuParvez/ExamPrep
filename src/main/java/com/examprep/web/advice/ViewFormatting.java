package com.examprep.web.advice;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Formatting helpers exposed to Thymeleaf as {@code ${fmt}}.
 */
@Component("fmt")
public class ViewFormatting {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public String dateTime(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant);
    }

    public String date(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    public String relative(Instant instant) {
        if (instant == null) {
            return "";
        }
        Duration elapsed = Duration.between(instant, Instant.now());
        long minutes = Math.max(0, elapsed.toMinutes());
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        long days = hours / 24;
        if (days < 30) {
            return days + (days == 1 ? " day ago" : " days ago");
        }
        return DATE_TIME.format(instant);
    }

    public String duration(int seconds) {
        int safe = Math.max(0, seconds);
        int minutes = safe / 60;
        int remainder = safe % 60;
        if (minutes == 0) {
            return remainder + " s";
        }
        return minutes + " min " + remainder + " s";
    }

    public String fileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kilobytes = bytes / 1024.0;
        if (kilobytes < 1024) {
            return String.format(Locale.ENGLISH, "%.1f KB", kilobytes);
        }
        return String.format(Locale.ENGLISH, "%.1f MB", kilobytes / 1024.0);
    }

    public String percentage(int part, int total) {
        if (total <= 0) {
            return "0%";
        }
        return Math.round(part * 100.0 / total) + "%";
    }
}
