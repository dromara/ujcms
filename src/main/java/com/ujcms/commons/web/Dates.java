package com.ujcms.commons.web;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.Date;

import static java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME;

/**
 * 日期工具类
 *
 * @author PONY
 */
public class Dates {
    /**
     * 时间纪元 `1970-01-01T00:00:00Z`
     */
    public static final OffsetDateTime EPOCH = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());

    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern(DATETIME_PATTERN);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern(DATE_PATTERN);

    public static LocalDateTime parseLocalDateTime(String text) {
        return LocalDateTime.ofInstant(parseInstant(text, Instant::from), ZoneId.systemDefault());
    }

    public static Instant parseInstant(String text) {
        return parseInstant(text, Instant::from);
    }

    public static OffsetDateTime parse(String text) {
        return parseInstant(text, OffsetDateTime::from);
    }

    public static ZonedDateTime parseZonedDateTime(String text) {
        return parseInstant(text, ZonedDateTime::from);
    }

    public static LocalDate parseLocalDate(String text) {
        return LocalDate.parse(text);
    }

    private static <T> T parseInstant(String text, TemporalQuery<T> query) {
        int length = text.length();
        if (length > DATETIME_PATTERN.length()) {
            return ISO_ZONED_DATE_TIME.parse(text, query);
        } else if (length == DATETIME_PATTERN.length()) {
            // ISO标准日期格式中带有字符'T'。如：2008-10-01T08:00
            String isoFlag = "T";
            if (text.contains(isoFlag)) {
                return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).query(query);
            } else {
                return LocalDateTime.parse(text, DATETIME_FORMAT).atZone(ZoneId.systemDefault()).query(query);
            }
        } else if (length == DATE_PATTERN.length()) {
            return LocalDate.parse(text).atStartOfDay().atZone(ZoneId.systemDefault()).query(query);
        } else {
            throw new DateTimeParseException("Text '" + text + "' could not be parsed", text, 0);
        }
    }

    public static String formatDate(OffsetDateTime offsetDateTime) {
        return LocalDateTime.ofInstant(offsetDateTime.toInstant(), ZoneId.systemDefault()).format(DATE_FORMAT);
    }

    public static String formatDateTime(OffsetDateTime offsetDateTime) {
        return LocalDateTime.ofInstant(offsetDateTime.toInstant(), ZoneId.systemDefault()).format(DATETIME_FORMAT);
    }

    public static String formatDate(ZonedDateTime zonedDateTime) {
        return LocalDateTime.ofInstant(zonedDateTime.toInstant(), ZoneId.systemDefault()).format(DATE_FORMAT);
    }

    public static String formatDateTime(ZonedDateTime zonedDateTime) {
        return LocalDateTime.ofInstant(zonedDateTime.toInstant(), ZoneId.systemDefault()).format(DATETIME_FORMAT);
    }

    public static String formatDate(LocalDateTime localDateTime) {
        return localDateTime.format(DATE_FORMAT);
    }

    public static String formatDateTime(LocalDateTime localDateTime) {
        return localDateTime.format(DATETIME_FORMAT);
    }

    public static String formatDate(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(DATE_FORMAT);
    }

    public static String formatDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(DATETIME_FORMAT);
    }

    public static String formatDate(LocalDate localDate) {
        return localDate.toString();
    }

    public static OffsetDateTime from(TemporalAccessor temporal) {
        if (temporal instanceof OffsetDateTime) {
            return (OffsetDateTime) temporal;
        }
        if (temporal instanceof ZonedDateTime) {
            return ((ZonedDateTime) temporal).toOffsetDateTime();
        }
        if (temporal instanceof Instant) {
            return OffsetDateTime.ofInstant((Instant) temporal, ZoneId.systemDefault());
        }
        if (temporal instanceof LocalDateTime) {
            return ((LocalDateTime) temporal).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        if (temporal instanceof LocalDate) {
            return ((LocalDate) temporal).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        }
        return OffsetDateTime.from(temporal);
    }

    public static OffsetDateTime ofDate(Date date) {
        return OffsetDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    public static int dayOfQuarter(LocalDate date) {
        int month = date.getMonthValue();
        // 取每个季度第一个月
        date = date.minusMonths((month - 1) % 3);
        // 取每个月的第一天
        date = date.withDayOfMonth(1);
        return date.getDayOfYear();
    }

    private Dates() {
        throw new IllegalStateException("Utility class");
    }
}
