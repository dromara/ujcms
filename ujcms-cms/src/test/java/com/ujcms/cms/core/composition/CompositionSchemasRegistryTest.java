package com.ujcms.cms.core.composition;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 已发布 Schema 注册表完整性测试：classpath {@code composition/schema/} 为唯一事实来源
 * （§8.4 无第二套校验规则可漂移，经 {@code GET /api/composition/schemas} 对外发布）。
 * 校验 {@link CompositionSchemas} 注册的每个 Schema 均可在 classpath 解析，
 * 且 {@code src/main/resources/composition/schema/} 下不存在未注册的 {@code *.schema.json}
 * （surefire 工作目录即模块目录，按模块相对路径列目录，不向上遍历仓库）。
 */
class CompositionSchemasRegistryTest {
    // Phase D：seed-cards / seed-placements Schema 已随卡片目录与经典投放种子退役
    private static final Path SCHEMA_SOURCE_DIR = Paths.get("src/main/resources/composition/schema");

    @Test
    void registeredSchemasResolveOnClasspath() {
        // 构造器逐一从 classpath 加载并解析全部注册 Schema，任一缺失/非法即抛异常
        Set<String> names = new CompositionSchemas().getRawSchemas().keySet();
        for (String name : names) {
            assertTrue(new ClassPathResource("composition/schema/" + name + ".schema.json").exists(),
                    "classpath 缺少已注册 Schema: " + name);
        }
    }

    @Test
    void schemaDirContainsNoUnregisteredSchemas() throws IOException {
        assertTrue(Files.isDirectory(SCHEMA_SOURCE_DIR),
                "未找到 " + SCHEMA_SOURCE_DIR + "（surefire 工作目录应为模块目录）");
        Set<String> registered = new CompositionSchemas().getRawSchemas().keySet();
        try (var stream = Files.list(SCHEMA_SOURCE_DIR)) {
            List<String> extras = stream.map(p -> p.getFileName().toString())
                    .filter(f -> f.endsWith(".schema.json"))
                    .filter(f -> !registered.contains(f.replace(".schema.json", "")))
                    .toList();
            assertEquals(List.of(), extras, "存在未在 CompositionSchemas 注册的 Schema");
        }
    }
}
