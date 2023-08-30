package com.ujcms.cms.ext.domain;

import com.ujcms.cms.ext.domain.base.VisitStatBase;
import com.ujcms.cms.ext.domain.support.StatDateString;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;

/**
 * 访问统计实体类
 *
 * @author PONY
 */
public class VisitStat extends VisitStatBase implements StatDateString, Serializable {
    private static final long serialVersionUID = 1L;

    public VisitStat() {
    }

    public VisitStat(String dateString, LocalDateTime date) {
        setDateString(dateString);
        setDate(date);
    }

    public static <T extends StatDateString> List<T> fillEmptyDate(
            List<T> list, @Nullable String begin, String end,
            DateTimeFormatter formatter, DateTimeFormatter displayFormatter,
            UnaryOperator<LocalDateTime> dateOperator, BiFunction<String, LocalDateTime, T> creator) {
        List<T> fullList = new ArrayList<>();
        Iterator<T> iterator = list.iterator();
        T bean = iterator.hasNext() ? iterator.next() : null;
        if (begin == null) {
            begin = bean != null ? bean.getDateString() : LocalDateTime.now().format(formatter);
        }
        LocalDateTime date = LocalDateTime.parse(begin, formatter);
        LocalDateTime endDate = LocalDateTime.parse(end, formatter);
        while (date.compareTo(endDate) <= 0) {
            String dateString = date.format(formatter);
            if (bean != null && dateString.equals(bean.getDateString())) {
                bean.setDateString(date.format(displayFormatter));
                bean.setDate(date);
                fullList.add(bean);
                bean = iterator.hasNext() ? iterator.next() : null;
            } else {
                fullList.add(creator.apply(date.format(displayFormatter), date));
            }
            date = dateOperator.apply(date);
        }
        return fullList;
    }

    private LocalDateTime date = LocalDateTime.now();

    public LocalDateTime getDate() {
        return date;
    }

    @Override
    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    /**
     * FOREACH条数
     * <p>
     * SqlServer 参数限制为2100个，Oracle、PostgreSQL参数限制为32767个，MySQL参数限制为65535个。
     * <p>
     * VisitStat表有8个字段，200条记录为1600个参数。
     */
    public static final int FOREACH_SIZE = 200;
    /**
     * 统计最大数量
     */
    public static final int STAT_MAX_SIZE = 100_000;
    /**
     * 显示最大数量
     */
    public static final int DISPLAY_MAX_SIZE = 1_000;
    /**
     * 统计日期格式：月
     */
    public static final DateTimeFormatter MONTH_FORMATTER = new DateTimeFormatterBuilder().appendPattern("yyyyMM")
            .parseDefaulting(DAY_OF_MONTH, 1).parseDefaulting(ChronoField.HOUR_OF_DAY, 1).toFormatter();
    /**
     * 统计日期显示格式：月
     */
    public static final DateTimeFormatter MONTH_DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    /**
     * 统计日期格式：天
     */
    public static final DateTimeFormatter DAY_FORMATTER = new DateTimeFormatterBuilder().appendPattern("yyyyMMdd")
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 1).toFormatter();
    /**
     * 统计日期显示格式：天
     */
    public static final DateTimeFormatter DAY_DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    /**
     * 统计日期格式：时
     */
    public static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");
    /**
     * 统计日期显示格式：时
     */
    public static final DateTimeFormatter HOUR_DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd'T'HH");
    /**
     * 统计日期格式：分
     */
    public static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    /**
     * 统计日期显示格式：分
     */
    public static final DateTimeFormatter MINUTE_DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 来源：直接访问
     */
    public static final String SOURCE_DIRECT = "DIRECT";
    /**
     * 来源：站内链接
     */
    public static final String SOURCE_INNER = "INNER";
    /**
     * 来源：站外链接
     */
    public static final String SOURCE_OUTER = "OUTER";
    /**
     * 来源：搜索引擎
     */
    public static final String SOURCE_SEARCH = "SEARCH";

    /**
     * 搜索引擎域名
     */
    public static final List<String> SEARCH_ENGINE_LIST = Collections.unmodifiableList(Arrays.asList(
            "baidu.com", "www.baidu.com", "m.baidu.com",
            "bing.com", "www.bing.com", "m.bing.com",
            "sogou.com", "www.sogou.com", "m.sogou.com",
            "360.cn", "www.360.cn", "m.360.cn",
            "google.com", "www.google.com", "m.google.com",
            "google.com.hk", "www.google.com.hk", "m.google.com.hk"));


    /**
     * 数据最多保留天数（三年）
     */
    public static final int DATA_MAX_DAY = 1096;

    /**
     * 类型：访客
     */
    public static final short TYPE_VISITOR = 1;
    /**
     * 类型：来源
     */
    public static final short TYPE_SOURCE = 2;
    /**
     * 类型：国家
     */
    public static final short TYPE_COUNTRY = 3;
    /**
     * 类型：省份
     */
    public static final short TYPE_PROVINCE = 4;
    /**
     * 类型：设备
     */
    public static final short TYPE_DEVICE = 5;
    /**
     * 类型：操作系统
     */
    public static final short TYPE_OS = 6;
    /**
     * 类型：浏览器
     */
    public static final short TYPE_BROWSER = 7;
    /**
     * 类型：来源类型
     */
    public static final short TYPE_SOURCE_TYPE = 8;
}