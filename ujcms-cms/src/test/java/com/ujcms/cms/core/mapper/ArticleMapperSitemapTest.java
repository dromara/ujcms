package com.ujcms.cms.core.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * sitemap 映射契约（docs/spec/sitemap/plan.md D1/D2/D3/D10）：解析 mapper XML，不连数据库。
 * <p>
 * 锁定两处曾经的静默缺陷：{@code SitemapResultMap} 未映射 {@code modified_}（本项目未开启
 * mapUnderscoreToCamelCase，含嵌套 resultMap 的映射不做自动映射，lastmod 因而恒为请求时间）；
 * 可收录条件（状态、非链接）曾在 Java 侧 LIMIT 之后过滤，页数与页内容不一致。
 */
class ArticleMapperSitemapTest {
    private static final String NS = "com.ujcms.cms.core.mapper.ArticleMapper.";
    private static final Map<String, Object> PARAMS = Map.of("siteId", 1L, "status", List.of((short) 0, (short) 1));

    private static Configuration configuration;

    @BeforeAll
    static void setUp() throws Exception {
        configuration = new Configuration();
        // 嵌套 resultMap（ArticleExtMapper.BaseResultMap）与 Seq 片段须先解析
        for (String resource : new String[]{"com/ujcms/cms/core/mapper/SeqMapper.xml",
                "com/ujcms/cms/core/mapper/ArticleExtMapper.xml",
                "com/ujcms/cms/core/mapper/ChannelMapper.xml",
                "com/ujcms/cms/core/mapper/ArticleMapper.xml"}) {
            try (InputStream in = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void sitemapResultMapMapsTheDateColumnsExplicitly() {
        ResultMap resultMap = configuration.getResultMap(NS + "SitemapResultMap");
        Set<String> properties = resultMap.getPropertyResultMappings().stream()
                .map(ResultMapping::getProperty).collect(Collectors.toSet());
        assertTrue(properties.contains("modified"), "lastmod source must be mapped: " + properties);
        assertTrue(properties.contains("created"), "static article paths derive from created: " + properties);
        assertTrue(properties.contains("status"));
        // 栏目别名经 join 映射为嵌套 Channel（无逐行嵌套查询）
        ResultMapping channel = resultMap.getPropertyResultMappings().stream()
                .filter(m -> "channel".equals(m.getProperty())).findFirst().orElseThrow();
        assertEquals(null, channel.getNestedQueryId(), "channel must not be a nested select (N+1)");
        Set<String> channelProperties = configuration.getResultMap(channel.getNestedResultMapId())
                .getPropertyResultMappings().stream().map(ResultMapping::getProperty).collect(Collectors.toSet());
        assertTrue(channelProperties.contains("slug"), "channel slug needed for URL assembly: " + channelProperties);
    }

    @Test
    void listBySiteIdForSitemapFiltersEligibilityInSql() {
        String sql = sql("listBySiteIdForSitemap");
        assertTrue(sql.contains("t.status_ in (?, ?)"), "status filter belongs to SQL, not Java: " + sql);
        assertTrue(sql.contains("uae.link_url_ is null"), "link articles filtered in SQL: " + sql);
        assertTrue(sql.contains("join ujcms_channel c on t.channel_id_ = c.id_"), "channel slug by join: " + sql);
        assertTrue(sql.contains("c.slug_ as channel_slug_"), sql);
        assertTrue(sql.contains("uae.image_"), "thumbnail feeds the image sitemap extension: " + sql);
        assertTrue(sql.trim().endsWith("order by t.id_"), "id-ascending keeps old pages stable: " + sql);
        assertFalse(sql.contains("minId"), "boundary-id paging removed in favour of offset paging");
    }

    @Test
    void listModifiedForSitemapIsASingleOrderedColumn() {
        MappedStatement statement = configuration.getMappedStatement(NS + "listModifiedForSitemap");
        assertEquals(OffsetDateTime.class, statement.getResultMaps().get(0).getType());
        String sql = sql("listModifiedForSitemap");
        assertTrue(sql.contains("t.status_ in (?, ?)"), sql);
        assertTrue(sql.contains("uae.link_url_ is null"), "same eligibility as the page query: " + sql);
        assertTrue(sql.trim().endsWith("order by t.id_"), "same order as the page query: " + sql);
    }

    @Test
    void listChannelModifiedForSitemapGroupsByAncestorViaClosureTable() {
        String sql = sql("listChannelModifiedForSitemap");
        assertTrue(sql.contains("join ujcms_channel_tree ct on ct.descendant_id_ = t.channel_id_"), sql);
        assertTrue(sql.contains("group by ct.ancestor_id_"), sql);
        ResultMap resultMap = configuration.getResultMap(NS + "SitemapChannelModifiedResultMap");
        Set<String> properties = resultMap.getPropertyResultMappings().stream()
                .map(ResultMapping::getProperty).collect(Collectors.toSet());
        assertTrue(properties.contains("modified"), properties.toString());
    }

    @Test
    void channelBaseResultMapMapsModifiedForChannelLastmod() {
        // 栏目 sitemap 的 lastmod 取 max(子树最新文章, 栏目自身 modified)：栏目查询只选 id_/slug_/type_/modified_
        Set<String> properties = configuration.getResultMap("com.ujcms.cms.core.mapper.ChannelMapper.BaseResultMap")
                .getPropertyResultMappings().stream().map(ResultMapping::getProperty).collect(Collectors.toSet());
        assertTrue(properties.contains("modified"), properties.toString());
        String sql = configuration.getMappedStatement("com.ujcms.cms.core.mapper.ChannelMapper.listByChannelForSitemap")
                .getBoundSql(Map.of("siteId", 1L)).getSql();
        assertTrue(sql.contains("t.modified_"), sql);
    }

    @Test
    void boundaryStatementsAreGone() {
        assertFalse(configuration.hasStatement(NS + "statForSitemap"), "aggregate-with-LIMIT boundary query removed");
        assertFalse(configuration.hasStatement("com.ujcms.cms.core.mapper.ChannelMapper.selectForSitemap"),
                "per-row channel lookup removed");
    }

    private static String sql(String statementId) {
        // 归一化空白：foreach 生成 "in ( ? , ? )"，断言按 "in (?, ?)" 书写
        return configuration.getMappedStatement(NS + statementId).getBoundSql(PARAMS).getSql()
                .replaceAll("\\s+", " ").replace("( ", "(").replace(" )", ")").replace(" ,", ",");
    }
}
