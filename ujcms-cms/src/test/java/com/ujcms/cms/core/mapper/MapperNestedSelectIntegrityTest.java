package com.ujcms.cms.core.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全量 mapper XML 引用完整性：把 classpath 上所有 mapper XML（本模块为 core 与 ext）解析进同一个
 * {@link Configuration}，断言跨命名空间的嵌套查询（{@code <association select="…">} /
 * {@code <collection select="…">}）、嵌套 resultMap、cache-ref 与 SQL 片段全部可解析。
 * <p>
 * MyBatis 对 {@code select="ns.id"} 只在首次执行该 resultMap 时才查找语句，删除或重命名被引用语句
 * 不会在启动时报错，只会在线上首次命中时抛
 * {@code Mapped Statements collection does not contain value for …}（2026-09-09 删除
 * {@code ChannelMapper.selectForSitemap} 后 {@code ArticleI18nMapper.ArticleRefResultMap} 即如此失效）。
 * 商业模块另有同名测试覆盖 t2–t4 对 core 语句（如 {@code UserMapper.selectRefer}）的引用。
 */
class MapperNestedSelectIntegrityTest {
    /** core / ext / commercial t{N} 的 mapper XML 都位于 com/ujcms/cms/<模块>/mapper 下 */
    private static final String MAPPER_PATTERN = "classpath*:com/ujcms/cms/**/mapper/*Mapper.xml";
    /** core + ext 的文件数下限；商业层裁剪发行版不含 t2–t4 时同样成立 */
    private static final int MIN_MAPPER_FILES = 80;

    private static Configuration configuration;
    private static List<String> parsed;

    @BeforeAll
    static void setUp() throws Exception {
        configuration = new Configuration();
        // 同一目录可能散落在多个 classpath 根（目录或依赖 jar），按 URL 排序保证解析顺序稳定
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(MAPPER_PATTERN);
        Arrays.sort(resources, Comparator.comparing(r -> {
            try {
                return r.getURL().toString();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }));
        parsed = new ArrayList<>(resources.length);
        for (Resource resource : resources) {
            String id = resource.getURL().toString();
            try (InputStream in = resource.getInputStream()) {
                // 跨文件引用（其他命名空间的 resultMap / cache-ref / 片段）由 MyBatis 延迟到后续文件解析后补齐
                new XMLMapperBuilder(in, configuration, id, configuration.getSqlFragments()).parse();
            }
            parsed.add(id);
        }
    }

    @Test
    void everyDeferredElementResolvesAfterAllMappersAreParsed() {
        assertTrue(parsed.size() >= MIN_MAPPER_FILES, "expected core + ext mapper files, parsed " + parsed.size());
        // reportUnresolved=true：仍有无法解析的元素时抛 IncompleteElementException，异常信息指明缺失的目标
        assertDoesNotThrow(() -> configuration.parsePendingResultMaps(true), "unresolved resultMap references");
        assertDoesNotThrow(() -> configuration.parsePendingCacheRefs(true), "unresolved cache-ref namespaces");
        assertDoesNotThrow(() -> configuration.parsePendingStatements(true),
                "unresolved statements (missing SQL fragment?)");
    }

    @Test
    void everyNestedSelectPointsAtAnExistingStatement() {
        List<String> dangling = new ArrayList<>();
        for (String name : configuration.getResultMapNames()) {
            // getResultMapNames 同时含短名与全限定名，仅取全限定名避免重复
            if (!name.contains(".")) {
                continue;
            }
            ResultMap resultMap = configuration.getResultMap(name);
            for (ResultMapping mapping : resultMap.getResultMappings()) {
                String nestedQueryId = mapping.getNestedQueryId();
                if (nestedQueryId != null && !configuration.hasStatement(nestedQueryId, false)) {
                    dangling.add(name + "." + mapping.getProperty() + " -> " + nestedQueryId);
                }
                String nestedResultMapId = mapping.getNestedResultMapId();
                if (nestedResultMapId != null && !configuration.hasResultMap(nestedResultMapId)) {
                    dangling.add(name + "." + mapping.getProperty() + " -> resultMap " + nestedResultMapId);
                }
            }
        }
        assertTrue(dangling.isEmpty(), "nested select/resultMap references to missing targets: " + dangling);
    }
}
