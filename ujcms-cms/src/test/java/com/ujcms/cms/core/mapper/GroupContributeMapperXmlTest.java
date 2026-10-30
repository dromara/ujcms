package com.ujcms.cms.core.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GroupContributeMapper 语句形状测试（docs/spec/member-contribute/plan.md）：只解析真实 XML 并断言 BoundSql 文本，
 * 不连数据库——XML 写坏、语句 ID 与接口对不上，在这里而不是在启动后暴露。
 */
class GroupContributeMapperXmlTest {
    private static final String NS = "com.ujcms.cms.core.mapper.GroupContributeMapper.";

    private static Configuration configuration;

    @BeforeAll
    static void setUp() throws Exception {
        configuration = new Configuration();
        String resource = "com/ujcms/cms/core/mapper/GroupContributeMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    private static String sql(String id, Map<String, Object> params) {
        return configuration.getMappedStatement(NS + id).getBoundSql(params).getSql();
    }

    @Test
    void everyInterfaceStatementIsMapped() {
        for (String id : new String[]{"insert", "listGroupByChannelId", "listChannelByGroupId", "listBySiteId",
                "deleteByChannelId", "deleteByGroupId", "deleteBySiteId"}) {
            assertTrue(configuration.hasStatement(NS + id), id);
        }
    }

    @Test
    void channelLookupAndSiteCleanupTargetTheRightColumns() {
        assertTrue(sql("listGroupByChannelId", Map.of("channelId", 1L)).contains("where t.channel_id_ = ?"));
        String byGroup = sql("listChannelByGroupId", Map.of("groupId", 1L, "siteId", 2L));
        assertTrue(byGroup.contains("t.group_id_ = ?") && byGroup.contains("t.site_id_ = ?"), byGroup);
        assertTrue(sql("deleteBySiteId", Map.of("siteId", 1L)).contains("where site_id_ = ?"));
        String bySite = sql("listBySiteId", Map.of("siteId", 1L));
        assertTrue(bySite.contains("t.group_id_, t.channel_id_, t.site_id_") && bySite.contains("where t.site_id_ = ?"), bySite);
    }
}
