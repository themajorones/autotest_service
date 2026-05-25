package dev.themajorones.ats.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class DateTimeUtil {
    
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    private static final DateTimeFormatter STANDARD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public static Long getCurrentTimestamp() {
        return System.currentTimeMillis() / 1000;
    }
    
    public static LocalDateTime timestampToLocalDateTime(Long timestamp) {
        if (timestamp == null) {
            return null;
        }
        return LocalDateTime.ofInstant(
            Instant.ofEpochSecond(timestamp),
            SYSTEM_ZONE
        );
    }
    
    public static Date timestampToDate(Long timestamp) {
        if (timestamp == null) {
            return null;
        }
        return new Date(timestamp * 1000);
    }
    
    public static String timestampToIsoString(Long timestamp) {
        if (timestamp == null) {
            return null;
        }
        LocalDateTime dateTime = timestampToLocalDateTime(timestamp);
        return dateTime.format(ISO_FORMATTER);
    }
    
    public static String timestampToString(Long timestamp) {
        if (timestamp == null) {
            return null;
        }
        LocalDateTime dateTime = timestampToLocalDateTime(timestamp);
        return dateTime.format(STANDARD_FORMATTER);
    }

    public static Long localDateTimeToTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(SYSTEM_ZONE).toEpochSecond();
    }
    
    public static Long dateToTimestamp(Date date) {
        if (date == null) {
            return null;
        }
        return date.getTime() / 1000;
    }

    public static Long stringToTimestamp(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        LocalDateTime dateTime = LocalDateTime.parse(dateString, STANDARD_FORMATTER);
        return localDateTimeToTimestamp(dateTime);
    }
    
    public static boolean isFuture(Long timestamp) {
        if (timestamp == null) {
            return false;
        }
        return timestamp > getCurrentTimestamp();
    }
    
    public static boolean isPast(Long timestamp) {
        if (timestamp == null) {
            return false;
        }
        return timestamp < getCurrentTimestamp();
    }
    
    public static Long secondsUntil(Long futureTimestamp) {
        if (futureTimestamp == null) {
            return null;
        }
        return futureTimestamp - getCurrentTimestamp();
    }
}
