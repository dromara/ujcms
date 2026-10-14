package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleExportRow;
import com.ujcms.cms.core.domain.ArticleTagName;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.service.args.ChannelArgs;
import org.apache.commons.lang3.StringUtils;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 文章列表导出为 .xlsx（docs/spec/article-management/plan.md D6、Phase 5a）。
 * <p>
 * 导出的是“列表”：标题、栏目、状态、日期、作者、浏览量等，加上所选栏目模型的自定义字段。不含正文——一个单元格放不下
 * 一篇文章（上限 32767 个字符）。范围与文章列表完全一致：同一组查询条件、同一个栏目、同一份数据权限、同一个排序。
 * <p>
 * 流式写出：按列表的顺序分批取 ID，每批再用一条关联语句取出要导出的列，写完一批即刷出，不在内存里攒下整张表，
 * 也不为每一行加载整张对象图。
 *
 * @author PONY
 */
@Service
public class ArticleExportService {
    private static final Logger logger = LoggerFactory.getLogger(ArticleExportService.class);
    /**
     * 一次最多导出的行数。更多的请加筛选条件分几次导出
     */
    public static final int MAX_ROWS = 50_000;
    /**
     * 每批取的行数
     */
    private static final int CHUNK = 500;
    /**
     * 自定义字段列的键前缀：{@code custom.<字段代码>}
     */
    public static final String CUSTOM_PREFIX = "custom.";
    /**
     * 可导出的系统列
     */
    public static final Set<String> SYSTEM_COLUMNS = Set.of("id", "title", "subtitle", "fullTitle", "slug", "channel",
            "status", "type", "sticky", "publishDate", "onlineDate", "offlineDate", "author", "editor", "source",
            "description", "seoKeywords", "tags", "linkUrl", "url", "org", "user", "created", "modifiedUser",
            "modified", "views", "comments", "ups", "downs");
    private static final Pattern CUSTOM_CODE = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LIST_SEPARATOR = ", ";
    private static final String PATH_SEPARATOR = " / ";
    private static final Set<String> WIDE_COLUMNS = Set.of("title", "fullTitle", "description", "url", "linkUrl");

    private final ArticleService articleService;
    private final ChannelService channelService;

    public ArticleExportService(ArticleService articleService, ChannelService channelService) {
        this.articleService = articleService;
        this.channelService = channelService;
    }

    /**
     * 一列：键与表头文字。表头文字由控制台给出——它已按操作员的语言与模型里的字段名称翻译好
     */
    public record Column(String key, String label) {
    }

    public static boolean isValidColumn(String key) {
        if (SYSTEM_COLUMNS.contains(key)) {
            return true;
        }
        return key.startsWith(CUSTOM_PREFIX) && CUSTOM_CODE.matcher(key.substring(CUSTOM_PREFIX.length())).matches();
    }

    /**
     * 写出工作簿
     *
     * @param args    查询条件，调用方已加上站点、栏目与数据权限
     * @param columns 要导出的列，键须已通过 {@link #isValidColumn}
     * @param labels  枚举值的文字：{@code status.0}、{@code type.1}、{@code true}、{@code false}。没有给的原样写出数值
     * @param site    当前站点，计算访问地址用
     * @return 写出的行数（不含表头）
     */
    public int write(ArticleArgs args, List<Column> columns, Map<String, String> labels, Site site, OutputStream out)
            throws IOException {
        Map<Long, Channel> channels = new HashMap<>(64);
        for (Channel channel : channelService.selectList(ChannelArgs.of().siteId(site.getId()))) {
            channels.put(channel.getId(), channel);
        }
        boolean withTags = columns.stream().anyMatch(column -> "tags".equals(column.key()));
        int written = 0;
        try (Workbook workbook = new Workbook(out, "UJCMS", null)) {
            Worksheet sheet = workbook.newWorksheet("Articles");
            for (int i = 0; i < columns.size(); i++) {
                sheet.inlineString(0, i, columns.get(i).label());
                sheet.style(0, i).bold().set();
                sheet.width(i, WIDE_COLUMNS.contains(columns.get(i).key()) ? 48 : 20);
            }
            sheet.freezePane(0, 1);
            Set<Long> seen = new HashSet<>();
            for (int offset = 0; offset < MAX_ROWS; offset += CHUNK) {
                List<Long> page = articleService.selectIds(args, offset, CHUNK);
                // 分批期间别人新增、删除了文章，行会前后挪动：已经写过的不再写第二遍
                List<Long> ids = new ArrayList<>(page.size());
                for (Long id : page) {
                    if (seen.add(id)) {
                        ids.add(id);
                    }
                }
                Map<Long, ArticleExportRow> rows = new HashMap<>(ids.size() * 2);
                for (ArticleExportRow row : articleService.listForExport(ids)) {
                    rows.put(row.getId(), row);
                }
                Map<Long, List<String>> tags = withTags ? tagNames(ids) : Collections.emptyMap();
                for (Long id : ids) {
                    ArticleExportRow row = rows.get(id);
                    if (row == null) {
                        continue;
                    }
                    written++;
                    Channel channel = channels.get(row.getChannelId());
                    Map<String, Object> customs = customs(row, channel);
                    for (int i = 0; i < columns.size(); i++) {
                        writeCell(sheet, written, i, value(columns.get(i).key(), row, channel, channels, customs,
                                tags, labels, site));
                    }
                }
                sheet.flush();
                if (page.size() < CHUNK) {
                    break;
                }
            }
        }
        return written;
    }

    private Map<Long, List<String>> tagNames(Collection<Long> ids) {
        Map<Long, List<String>> tags = new HashMap<>(ids.size() * 2);
        for (ArticleTagName tag : articleService.listTagNames(ids)) {
            tags.computeIfAbsent(tag.getArticleId(), key -> new ArrayList<>()).add(tag.getName());
        }
        return tags;
    }

    private static Map<String, Object> customs(ArticleExportRow row, @Nullable Channel channel) {
        if (channel == null || StringUtils.isBlank(row.getMainsJson())) {
            return Collections.emptyMap();
        }
        try {
            return channel.getArticleModel().assembleMap(row.getMainsJson());
        } catch (RuntimeException e) {
            // 个别文章的自定义字段数据坏了，不该让整张表导不出来
            logger.warn("article {}: custom fields cannot be read: {}", row.getId(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Nullable
    private static Object value(String key, ArticleExportRow row, @Nullable Channel channel, Map<Long, Channel> channels,
                                Map<String, Object> customs, Map<Long, List<String>> tags,
                                Map<String, String> labels, Site site) {
        if (key.startsWith(CUSTOM_PREFIX)) {
            Object custom = customs.get(key.substring(CUSTOM_PREFIX.length()));
            // 开关字段：是 / 否的文字随请求给出（labels 的 true / false），与状态、类型一样按操作员的语言
            return custom instanceof Boolean bool
                    ? labels.getOrDefault(String.valueOf(bool), String.valueOf(bool)) : custom;
        }
        return switch (key) {
            // 19 位的 ID 写成数字会丢精度
            case "id" -> String.valueOf(row.getId());
            case "title" -> row.getTitle();
            case "subtitle" -> row.getSubtitle();
            case "fullTitle" -> row.getFullTitle();
            // 等于 ID 的别名是“未设别名”的占位值
            case "slug" -> row.getSlug().equals(String.valueOf(row.getId())) ? null : row.getSlug();
            case "channel" -> channelPath(channel, channels);
            case "status" -> labels.getOrDefault("status." + row.getStatus(), String.valueOf(row.getStatus()));
            case "type" -> labels.getOrDefault("type." + row.getType(), String.valueOf(row.getType()));
            case "sticky" -> row.getSticky() > 0 ? row.getSticky() : null;
            case "publishDate" -> row.getPublishDate();
            case "onlineDate" -> row.getOnlineDate();
            case "offlineDate" -> row.getOfflineDate();
            case "author" -> row.getAuthor();
            case "editor" -> row.getEditor();
            case "source" -> row.getSource();
            case "description" -> row.getSeoDescription();
            case "seoKeywords" -> row.getSeoKeywords();
            case "tags" -> tags.get(row.getId());
            case "linkUrl" -> row.getLinkUrl();
            case "url" -> url(row, channel, site);
            case "org" -> row.getOrgName();
            case "user" -> row.getUsername();
            case "created" -> row.getCreated();
            case "modifiedUser" -> row.getModifiedUsername();
            case "modified" -> row.getModified();
            case "views" -> row.getViews();
            case "comments" -> row.getComments();
            case "ups" -> row.getUps();
            case "downs" -> row.getDowns();
            default -> null;
        };
    }

    /**
     * 栏目的完整路径：一级栏目 / 二级栏目 / ……
     */
    @Nullable
    private static String channelPath(@Nullable Channel channel, Map<Long, Channel> channels) {
        if (channel == null) {
            return null;
        }
        LinkedList<String> names = new LinkedList<>();
        Channel current = channel;
        // 栏目树不该有环；万一数据有问题，走过的层数到顶就停
        for (int depth = 0; current != null && depth < 32; depth++) {
            names.addFirst(current.getName());
            current = current.getParentId() != null ? channels.get(current.getParentId()) : null;
        }
        return String.join(PATH_SEPARATOR, names);
    }

    /**
     * 文章的访问地址，与文章列表里“标题”链接指向的地址同一算法。借一个临时的文章对象来算，不另写一套规则
     */
    @Nullable
    private static String url(ArticleExportRow row, @Nullable Channel channel, Site site) {
        if (channel == null) {
            return null;
        }
        try {
            Article article = new Article();
            article.setId(row.getId());
            article.setSiteId(site.getId());
            article.setSite(site);
            article.setChannelId(row.getChannelId());
            article.setChannel(channel);
            article.setSlug(row.getSlug());
            if (row.getCreated() != null) {
                article.setCreated(row.getCreated());
            }
            article.getExt().setLinkUrl(row.getLinkUrl());
            article.getExt().setArticleStaticPath(row.getArticleStaticPath());
            return article.getFullUrl();
        } catch (RuntimeException e) {
            logger.warn("article {}: url cannot be built: {}", row.getId(), e.getMessage());
            return null;
        }
    }

    private static void writeCell(Worksheet sheet, int row, int column, @Nullable Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Number number) {
            sheet.value(row, column, number);
        } else {
            String text = text(value);
            if (!text.isEmpty()) {
                // 文字一律写成“内联字符串”：不进共享字符串表，几万行各不相同的标题不会在内存里攒着。
                // .xlsx 里字符串单元格不会被当成公式执行，以 = + - @ 开头的标题无须转义
                sheet.inlineString(row, column, truncate(text));
            }
        }
    }

    private static String text(Object value) {
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof OffsetDateTime dateTime) {
            return DATE_TIME.format(dateTime.atZoneSameInstant(ZoneId.systemDefault()));
        }
        if (value instanceof TemporalAccessor || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Collection<?> collection) {
            List<String> parts = new ArrayList<>(collection.size());
            for (Object item : collection) {
                if (item != null) {
                    parts.add(text(item));
                }
            }
            return String.join(LIST_SEPARATOR, parts);
        }
        return String.valueOf(value);
    }

    /**
     * Excel 一个单元格最多 32767 个字符
     */
    private static String truncate(String text) {
        int max = 32767;
        return text.length() > max ? text.substring(0, max) : text;
    }
}
