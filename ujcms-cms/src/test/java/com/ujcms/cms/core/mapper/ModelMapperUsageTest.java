package com.ujcms.cms.core.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ModelMapper 使用统计语句形状测试（docs/spec/model-management/plan.md M4）：只解析真实 XML 并断言 BoundSql
 * （不连数据库）。引用表不在模型的缓存域内，语句不得走二级缓存；每个指向 ujcms_model 的外键列都要计入——
 * 漏掉一列就是“删除时报原始数据库错误”的那类缺口（站点设置模型此前正是如此）。
 */
class ModelMapperUsageTest {
    private static final String NS = "com.ujcms.cms.core.mapper.ModelMapper.";
    private static final Path CHANGELOG_DIR = Path.of("src/main/resources/db/changelog");

    private static Configuration configuration;

    @BeforeAll
    static void setUp() throws Exception {
        configuration = new Configuration();
        for (String resource : new String[]{"com/ujcms/cms/core/mapper/SeqMapper.xml",
                "com/ujcms/cms/core/mapper/ModelMapper.xml", "com/ujcms/cms/core/mapper/SiteMapper.xml"}) {
            try (InputStream in = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }

    private static String usagesSql() {
        return configuration.getMappedStatement(NS + "selectUsages").getBoundSql(Map.of("siteId", 1L)).getSql();
    }

    @Test
    void usagesStatementIsUncachedAndMapsToRecord() {
        MappedStatement statement = configuration.getMappedStatement(NS + "selectUsages");
        assertFalse(statement.isUseCache(), "引用表的写入不会刷新模型缓存：统计不得走二级缓存");
        ResultMap resultMap = statement.getResultMaps().get(0);
        assertEquals("com.ujcms.cms.core.domain.ModelUsage", resultMap.getType().getName());
        assertEquals(List.of("model_id_", "usage_count_", "external_count_"),
                resultMap.getConstructorResultMappings().stream().map(m -> m.getColumn()).toList());
        // record 的 int 分量须映射为基本类型（javaType="_int"）：映射成 Integer 时 MyBatis 找不到构造器，只在真实查询时暴露
        assertEquals(List.of(Long.class, int.class, int.class),
                resultMap.getConstructorResultMappings().stream().map(m -> m.getJavaType()).toList());
        String sql = usagesSql();
        assertTrue(sql.contains("group by u.model_id_"), sql);
        assertTrue(sql.contains("u.site_id_ is null or u.site_id_ <> ?"), sql);
    }

    @Test
    void everyForeignKeyToModelIsCounted() throws IOException {
        String sql = usagesSql();
        List<String> columns = foreignKeyColumnsToModel();
        assertTrue(columns.size() >= 6, "变更日志里指向 ujcms_model 的外键: " + columns);
        for (String column : columns) {
            // 表.列：语句里引用表须出现，且该列名须出现（派生表各段用表别名，不逐段断言别名）
            String table = column.substring(0, column.indexOf('.'));
            String name = column.substring(column.indexOf('.') + 1);
            assertTrue(sql.contains(table) && sql.contains(name), "使用统计漏掉了外键列 " + column + "\n" + sql);
        }
    }

    @Test
    void siteMapperHasTheModelDeleteGuardStatement() {
        String sql = configuration.getMappedStatement("com.ujcms.cms.core.mapper.SiteMapper.existsByModelId")
                .getBoundSql(Map.of("modelId", 13L)).getSql();
        assertTrue(sql.contains("from ujcms_site t where t.model_id_ = ?"), sql);
    }

    /**
     * 扫描 Liquibase 变更日志：每个 {@code referencedTableName: ujcms_model} 外键的 {@code baseTableName.baseColumnNames}
     */
    private static List<String> foreignKeyColumnsToModel() throws IOException {
        // 外键块内不跨越别的 referencedTableName：否则紧邻的别表外键会被误计
        Pattern block = Pattern.compile("baseColumnNames: (\\w+)\\s+baseTableName: (\\w+)"
                + "(?:(?!referencedTableName)[\\s\\S]){0,400}?referencedTableName: ujcms_model\\b");
        List<String> columns = new ArrayList<>();
        try (Stream<Path> files = Files.list(CHANGELOG_DIR)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".yaml")).toList()) {
                Matcher matcher = block.matcher(Files.readString(file));
                while (matcher.find()) {
                    String column = matcher.group(2) + "." + matcher.group(1);
                    if (!columns.contains(column)) {
                        columns.add(column);
                    }
                }
            }
        }
        return columns;
    }
}
