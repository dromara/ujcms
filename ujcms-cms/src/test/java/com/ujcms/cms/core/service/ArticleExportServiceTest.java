package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.ArticleExportRow;
import com.ujcms.cms.core.domain.ArticleTagName;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ArticleExportService.Column;
import com.ujcms.cms.core.service.args.ArticleArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文章列表导出（docs/spec/article-management/plan.md D6、Phase 5a）：写出的是一份真的 .xlsx，解开压缩包读工作表的 XML
 * 来核对。按列表的顺序分批取 ID、每批一条关联语句取行；ID 写成文字（19 位数字不丢精度）；文字一律是内联字符串，
 * 以 = 开头的标题不会变成公式。
 */
class ArticleExportServiceTest {
    private static final long SITE = 1L;
    private static final long NEWS = 10L;
    private static final long WORLD = 11L;
    private static final Pattern CHAR_REF = Pattern.compile("&#x([0-9a-fA-F]+);");

    private ArticleService articleService;
    private ChannelService channelService;
    private ArticleExportService service;
    private final Site site = new Site();
    private final ArticleArgs args = ArticleArgs.of();

    @BeforeEach
    void setUp() {
        articleService = mock(ArticleService.class);
        channelService = mock(ChannelService.class);
        service = new ArticleExportService(articleService, channelService);
        site.setId(SITE);
        site.setProtocol("https");
        site.setDomain("example.com");

        Model model = new Model();
        model.setCustoms("[{\"code\":\"price\",\"type\":\"number\",\"precision\":2,\"name\":\"Price\"},"
                + "{\"code\":\"city\",\"type\":\"text\",\"name\":\"City\"},"
                + "{\"code\":\"featured\",\"type\":\"switch\",\"name\":\"Featured\"}]");
        Channel news = channel(NEWS, null, "News", model);
        Channel world = channel(WORLD, NEWS, "World", model);
        when(channelService.selectList(any())).thenReturn(List.of(news, world));
    }

    private Channel channel(long id, Long parentId, String name, Model model) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setParentId(parentId);
        channel.setSiteId(SITE);
        channel.setSite(site);
        channel.setName(name);
        channel.setSlug(name.toLowerCase());
        channel.setArticleModel(model);
        return channel;
    }

    private static ArticleExportRow row(long id, String title) {
        ArticleExportRow row = new ArticleExportRow();
        row.setId(id);
        row.setChannelId(WORLD);
        row.setTitle(title);
        row.setSlug(String.valueOf(id));
        row.setStatus((short) 0);
        row.setViews(42L);
        row.setCreated(OffsetDateTime.parse("2026-10-06T08:30:00+08:00"));
        row.setPublishDate(OffsetDateTime.parse("2026-10-06T09:00:00+08:00"));
        row.setMainsJson("{\"price\":12.5,\"city\":\"Paris\",\"featured\":true}");
        return row;
    }

    /**
     * 工作簿是流式写出的 Zip64：按中央目录读（{@link ZipFile}），不用逐条顺读的 ZipInputStream——后者读不了这种条目
     */
    private String sheetXml(byte[] xlsx) throws IOException {
        Path file = Files.createTempFile("article-export", ".xlsx");
        try {
            Files.write(file, xlsx);
            try (ZipFile zip = new ZipFile(file.toFile())) {
                ZipEntry entry = zip.getEntry("xl/worksheets/sheet1.xml");
                if (entry == null) {
                    throw new AssertionError("no worksheet in the workbook");
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    // 非 ASCII 字符写成了数字字符引用（&#x6807;）：还原成文字再比对
                    return CHAR_REF.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                            .replaceAll(match -> new String(Character.toChars(Integer.parseInt(match.group(1), 16))));
                }
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void writesHeaderAndRowsWithTypedCells() throws IOException {
        long id = 398009794180253697L;
        ArticleExportRow row = row(id, "=1+1 looks like a formula");
        when(articleService.selectIds(any(), eq(0), anyInt())).thenReturn(List.of(id));
        when(articleService.listForExport(any())).thenReturn(List.of(row));
        ArticleTagName first = new ArticleTagName();
        first.setArticleId(id);
        first.setName("alpha");
        ArticleTagName second = new ArticleTagName();
        second.setArticleId(id);
        second.setName("beta");
        when(articleService.listTagNames(any())).thenReturn(List.of(first, second));
        List<Column> columns = List.of(new Column("id", "ID"), new Column("title", "标题"), new Column("channel", "栏目"),
                new Column("status", "状态"), new Column("publishDate", "发布日期"), new Column("views", "浏览量"),
                new Column("tags", "TAG"), new Column("slug", "别名"), new Column("url", "地址"),
                new Column("custom.price", "价格"), new Column("custom.city", "城市"),
                new Column("custom.featured", "精选"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int written = service.write(args, columns, Map.of("status.0", "已发布", "true", "是", "false", "否"), site, out);

        assertEquals(1, written);
        String xml = sheetXml(out.toByteArray());
        // 表头
        assertTrue(xml.contains("<t>标题</t>"), xml);
        // ID 是文字，不是数字
        assertTrue(xml.contains("t=\"inlineStr\"") && xml.contains("<t>" + id + "</t>"), xml);
        // 以 = 开头的标题是字符串单元格，没有 <f> 公式
        assertTrue(xml.contains("<t>=1+1 looks like a formula</t>"), xml);
        assertFalse(xml.contains("<f>"), xml);
        // 栏目的完整路径、状态的文字、TAG 以逗号连接
        assertTrue(xml.contains("<t>News / World</t>"), xml);
        assertTrue(xml.contains("<t>已发布</t>"), xml);
        assertTrue(xml.contains("<t>alpha, beta</t>"), xml);
        // 日期按服务器时区写成文字
        String published = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(
                OffsetDateTime.parse("2026-10-06T09:00:00+08:00").atZoneSameInstant(ZoneId.systemDefault()));
        assertTrue(xml.contains("<t>" + published + "</t>"), xml);
        // 浏览量、自定义的数字字段是数字单元格
        assertTrue(xml.contains("<v>42</v>"), xml);
        assertTrue(xml.contains("<v>12.5</v>"), xml);
        assertTrue(xml.contains("<t>Paris</t>"), xml);
        // 开关字段按请求给的文字写“是 / 否”，不是 true / false
        assertTrue(xml.contains("<t>是</t>"), xml);
        assertFalse(xml.contains("<t>true</t>"), xml);
        // 等于 ID 的别名是“未设别名”的占位值：不写出；地址照常
        assertTrue(xml.contains("example.com") && xml.contains("/" + id), xml);
    }

    @Test
    void pagesByIdsAndNeverWritesARowTwice() throws IOException {
        // 第一批满 500 条；第二批里混进了已经写过的一条（分批期间有人新增了文章，行往后挪了一位）
        List<Long> first = new ArrayList<>();
        for (long i = 1; i <= 500; i++) {
            first.add(i);
        }
        when(articleService.selectIds(any(), eq(0), anyInt())).thenReturn(first);
        when(articleService.selectIds(any(), eq(500), anyInt())).thenReturn(List.of(500L, 501L));
        when(articleService.listForExport(any())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().map(id -> row(id, "t" + id)).toList();
        });
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int written = service.write(args, List.of(new Column("title", "Title")), Map.of(), site, out);

        assertEquals(501, written);
        String xml = sheetXml(out.toByteArray());
        assertEquals(1, xml.split("<t>t500</t>", -1).length - 1);
        assertTrue(xml.contains("<t>t501</t>"));
        // 没有要 TAG 列，就不查 TAG
        verify(articleService, never()).listTagNames(any());
        // 第二批不满，不再取第三批
        verify(articleService, never()).selectIds(any(), eq(1000), anyInt());
    }

    @Test
    void anEmptyResultStillGivesAWorkbookWithTheHeader() throws IOException {
        when(articleService.selectIds(any(), anyInt(), anyInt())).thenReturn(List.of());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int written = service.write(args, List.of(new Column("title", "Title")), Map.of(), site, out);

        assertEquals(0, written);
        assertTrue(sheetXml(out.toByteArray()).contains("<t>Title</t>"));
    }

    @Test
    void onlyKnownColumnsAndWellFormedCustomCodesAreAccepted() {
        assertTrue(ArticleExportService.isValidColumn("title"));
        assertTrue(ArticleExportService.isValidColumn("custom.price"));
        assertFalse(ArticleExportService.isValidColumn("text"));
        assertFalse(ArticleExportService.isValidColumn("custom."));
        assertFalse(ArticleExportService.isValidColumn("custom.a-b"));
        assertFalse(ArticleExportService.isValidColumn("password"));
    }
}
