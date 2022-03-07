package com.ofwise.util.freemarker;

import com.ujcms.core.web.support.Directives;
import freemarker.core.Environment;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * FreeMarker java8 日期格式化。解决 freemarker-java-8 不支持 Instant 的问题，
 * 以及 PostgreSQL 的 OffsetDateTime 不含正确时区的问题。
 * <ul>
 * <li>支持 Instant。使用 JVM 默认 TimeZone。
 * <li>支持 OffsetDateTime。使用 JVM 默认 TimeZone。不使用 OffsetDateTime 自带的 TimeZone，因为PostgreSQL返回的
 * OffsetDateTime 不带 TimeZone 信息，使用标准 UTC。
 * <li>支持 ZonedDateTime。使用 ZonedDateTime 自带的 TimeZone 信息。
 * </ul>
 *
 * @author liufang
 */
public class FormatMethod implements TemplateMethodModelEx {
    private DateTimeFormatter defaultOrPatternFormatted(final String pattern, final Locale locale) {
        switch (pattern) {
            case "SHORT":
                return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale);
            case "MEDIUM":
                return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale);
            case "LONG":
                return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).withLocale(locale);
            case "FULL":
                return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).withLocale(locale);
            default:
                return DateTimeFormatter.ofPattern(pattern, locale);
        }
    }

    @Override
    public Object exec(List args) throws TemplateModelException {
        int minArgsSize = 2;
        if (args.size() < minArgsSize) {
            throw new TemplateModelException("Arguments size must greater or equal to " + minArgsSize);
        }
        Temporal date = Freemarkers.getTemporalRequired((TemplateModel) args.get(0), "arg0");
        String pattern = Freemarkers.getStringRequired((TemplateModel) args.get(1), "arg1");

        Environment env = Environment.getCurrentEnvironment();
        Locale locale = Optional.ofNullable(Directives.findRequest(env)).map(RequestContextUtils::getLocale)
                .orElse(Locale.CHINA);

        DateTimeFormatter formatter = defaultOrPatternFormatted(pattern, locale);
        return formatter.format(zonedTime(date, ZoneId.systemDefault()));
    }

    /**
     * Creates a Temporal object filling the missing fields of the provided time with default values.
     *
     * @param target        the temporal object to be converted
     * @param defaultZoneId the default value for ZoneId
     * @return a Temporal object
     */
    private static ZonedDateTime zonedTime(final Object target, final ZoneId defaultZoneId) {
        Objects.requireNonNull(target, "Target cannot be null");
        Objects.requireNonNull(defaultZoneId, "ZoneId cannot be null");
        if (target instanceof Instant) {
            return ZonedDateTime.ofInstant((Instant) target, defaultZoneId);
        } else if (target instanceof LocalDate) {
            return ZonedDateTime.of((LocalDate) target, LocalTime.MIDNIGHT, defaultZoneId);
        } else if (target instanceof LocalDateTime) {
            return ZonedDateTime.of((LocalDateTime) target, defaultZoneId);
        } else if (target instanceof LocalTime) {
            return ZonedDateTime.of(LocalDate.now(), (LocalTime) target, defaultZoneId);
        } else if (target instanceof OffsetDateTime) {
            return ((OffsetDateTime) target).atZoneSameInstant(defaultZoneId);
        } else if (target instanceof OffsetTime) {
            LocalTime localTime = ((OffsetTime) target).toLocalTime();
            return ZonedDateTime.of(LocalDate.now(), localTime, defaultZoneId);
        } else if (target instanceof Year) {
            LocalDate localDate = ((Year) target).atDay(1);
            return ZonedDateTime.of(localDate, LocalTime.MIDNIGHT, defaultZoneId);
        } else if (target instanceof YearMonth) {
            LocalDate localDate = ((YearMonth) target).atDay(1);
            return ZonedDateTime.of(localDate, LocalTime.MIDNIGHT, defaultZoneId);
        } else if (target instanceof ZonedDateTime) {
            return (ZonedDateTime) target;
        } else {
            throw new IllegalArgumentException(
                    "Cannot format object of class \"" + target.getClass().getName() + "\" as a date");
        }
    }
}
