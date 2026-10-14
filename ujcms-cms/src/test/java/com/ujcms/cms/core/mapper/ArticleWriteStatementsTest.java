package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.ArticleExt;
import com.ujcms.cms.core.domain.generated.GeneratedArticle;
import com.ujcms.common.query.QueryParser;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文章的定向写入语句（docs/spec/article-management/plan.md Phase 1.1、1.6、D9）：解析 mapper XML，不连数据库。
 * <p>
 * 编辑保存用的 {@code updateContent} 是生成的 {@code update} 去掉服务端自管的四列——两条语句分别维护，
 * 给扩展表加列时漏改 {@code updateContent}，新字段就悄悄地存不进去。这里逐列比对。
 */
class ArticleWriteStatementsTest {
    private static final String EXT = "com.ujcms.cms.core.mapper.ArticleExtMapper.";
    private static final String ARTICLE = "com.ujcms.cms.core.mapper.ArticleMapper.";
    private static final String BUFFER = "com.ujcms.cms.core.mapper.ArticleBufferMapper.";
    private static final String CHANNEL_EXT = "com.ujcms.cms.core.mapper.ChannelExtMapper.";
    /**
     * 服务端自管的列：静态化、审批流程各自定向写入
     */
    private static final Set<String> SERVER_OWNED = Set.of(
            "static_file_", "mobile_static_file_", "process_instance_id_", "reject_reason_");
    private static final Pattern ASSIGNED = Pattern.compile("(\\w+_)\\s*=");

    private static Configuration configuration;

    @BeforeAll
    static void setUp() throws Exception {
        configuration = new Configuration();
        for (String resource : new String[]{"com/ujcms/cms/core/mapper/SeqMapper.xml",
                "com/ujcms/cms/core/mapper/ArticleExtMapper.xml",
                "com/ujcms/cms/core/mapper/ChannelExtMapper.xml",
                "com/ujcms/cms/core/mapper/ChannelMapper.xml",
                "com/ujcms/cms/core/mapper/ArticleBufferMapper.xml",
                "com/ujcms/cms/core/mapper/ArticleMapper.xml"}) {
            try (InputStream in = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }

    private static String sql(String id, Object parameter) {
        return configuration.getMappedStatement(id).getBoundSql(parameter).getSql().replaceAll("\\s+", " ").trim();
    }

    /**
     * SET 子句里被赋值的列
     */
    private static Set<String> assignedColumns(String sql) {
        String set = sql.substring(sql.indexOf(" set ") + 5, sql.lastIndexOf(" where "));
        Set<String> columns = new LinkedHashSet<>();
        Matcher matcher = ASSIGNED.matcher(set);
        while (matcher.find()) {
            columns.add(matcher.group(1));
        }
        return columns;
    }

    @Test
    void updateContentIsTheGeneratedUpdateMinusTheServerOwnedColumns() {
        Set<String> all = assignedColumns(sql(EXT + "update", new ArticleExt()));
        Set<String> content = assignedColumns(sql(EXT + "updateContent", new ArticleExt()));

        assertTrue(all.containsAll(SERVER_OWNED), "generated update lost a column: " + all);
        Set<String> expected = new LinkedHashSet<>(all);
        expected.removeAll(SERVER_OWNED);
        assertEquals(expected, content,
                "updateContent must list every content column of update (add new ext columns to both)");
    }

    @Test
    void theEditorialSaveNeverWritesCounters() {
        Set<String> content = assignedColumns(sql(EXT + "updateContent", new ArticleExt()));
        for (String counter : new String[]{"comments_", "downloads_", "favorites_", "ups_", "downs_", "views_",
                "day_views_", "week_views_", "month_views_", "quarter_views_", "year_views_"}) {
            assertFalse(content.contains(counter), counter + " must not be written by the editorial save");
        }
    }

    @Test
    void staticFileAndProcessStatementsWriteOnlyTheirOwnColumns() {
        Map<String, Object> params = Map.of("id", 1L);
        assertEquals(Set.of("static_file_", "mobile_static_file_"),
                assignedColumns(sql(EXT + "updateStaticFile", params)));
        assertEquals(Set.of("process_instance_id_", "reject_reason_"),
                assignedColumns(sql(EXT + "updateProcess", params)));
        // 栏目同理：静态化不回写栏目正文
        assertEquals(Set.of("static_file_", "mobile_static_file_"),
                assignedColumns(sql(CHANNEL_EXT + "updateStaticFile", params)));
    }

    @Test
    void statusAndStickyStatementsWriteOnlyTheirOwnColumns() {
        Map<String, Object> params = Map.of("id", 1L, "status", (short) 0, "sticky", (short) 1);
        assertEquals(Set.of("status_"), assignedColumns(sql(ARTICLE + "updateStatus", params)));
        assertEquals(Set.of("sticky_", "sticky_date_"), assignedColumns(sql(ARTICLE + "updateSticky", params)));
        assertEquals(Set.of("src_id_"), assignedColumns(sql(ARTICLE + "updateSrcIdToNull", params)));
    }

    @Test
    void countersAreIncrementedInTheDatabase() {
        assertTrue(sql(BUFFER + "incrementUps", 1L).contains("ups_ = ups_ + 1"));
        assertTrue(sql(BUFFER + "incrementDowns", 1L).contains("downs_ = downs_ + 1"));
        assertTrue(sql(BUFFER + "incrementDownloads", 1L).contains("downloads_ = downloads_ + 1"));
        // 读出、加一、整行写回的那条语句已删除：没有调用方还能把并发的计数盖掉
        assertFalse(configuration.hasStatement(BUFFER + "update"));
    }

    @Test
    void periodResetsTouchOnlyNonZeroRows() {
        assertTrue(sql(BUFFER + "resetDayViews", null).endsWith("where day_views_ <> 0"));
        assertTrue(sql(BUFFER + "resetWeekViews", null).endsWith("where week_views_ <> 0"));
        assertTrue(sql(BUFFER + "resetMonthViews", null).endsWith("where month_views_ <> 0"));
        assertTrue(sql(BUFFER + "resetQuarterViews", null).endsWith("where quarter_views_ <> 0"));
        assertTrue(sql(BUFFER + "resetYearViews", null).endsWith("where year_views_ <> 0"));
    }

    @Test
    void moveWritesOnlyTheChannelColumn() {
        // 批量移动栏目不动修改时间、修改人（Phase 3）
        assertEquals(Set.of("channel_id_"),
                assignedColumns(sql(ARTICLE + "updateChannel", Map.of("id", 1L, "channelId", 2L))));
    }

    @Test
    void statusCountsShareTheFromAndWhereOfTheList() {
        // 状态页签上的计数与列表用同一段 FROM / JOIN / WHERE（SeqMapper.Select_All_From）：条件改了两边一起变
        Map<String, Object> query = new HashMap<>();
        query.put("Contains_title", "news");
        query.put("EQ_channel-name", "World");
        Map<String, Object> params = new HashMap<>();
        params.put("queryInfo", QueryParser.parse(query, GeneratedArticle.TABLE_NAME, "order_desc,id_desc"));
        params.put("orgIds", List.of(3L));
        String list = sql(ARTICLE + "selectAll", params);
        String counts = sql(ARTICLE + "countByStatus", params);

        String shared = list.substring(list.indexOf(" FROM "), list.indexOf(" ORDER BY "));
        assertTrue(shared.contains(" JOIN ") && shared.contains(" WHERE "), shared);
        assertEquals("SELECT t.status_ AS status_, COUNT(DISTINCT t.id_) AS total_" + shared + " GROUP BY t.status_",
                counts);
    }
}
