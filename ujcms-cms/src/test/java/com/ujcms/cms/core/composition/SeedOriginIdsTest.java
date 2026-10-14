package com.ujcms.cms.core.composition;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * originId 铸造与来源分类测试（theme-seed-export §4/§4.1）：铸造为纯函数——同输入恒同输出
 * （站内连续导出稳定），CJK 标题走与ID无关的内容散列回退，冲突后缀确定。
 */
class SeedOriginIdsTest {

    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void channelAndTopicMintFromAlias() {
        assertEquals("news", SeedOriginIds.mintAliased("news", "c-", HASH, new HashSet<>()));
        // 别名空缺回退内容散列形（与行ID无关——重导出稳定）
        assertEquals("c-01234567", SeedOriginIds.mintAliased(null, "c-", HASH, new HashSet<>()));
        assertEquals("t-01234567", SeedOriginIds.mintAliased(" ", "t-", HASH, new HashSet<>()));
    }

    @Test
    void articleMintPrefersAliasThenSlugThenHash() {
        assertEquals("a-my-story", SeedOriginIds.mintArticle("my-story", "任意标题", HASH, new HashSet<>()));
        assertEquals("a-hello-world-2026",
                SeedOriginIds.mintArticle(null, "Hello, World 2026!", HASH, new HashSet<>()));
        // CJK 标题无拼音转写：slug 为空 → 内容散列回退（§4.1）
        assertEquals("a-01234567", SeedOriginIds.mintArticle(null, "欢迎使用全新主题", HASH, new HashSet<>()));
        // 混合标题保留 ASCII 段
        assertEquals("a-ujcms-11", SeedOriginIds.mintArticle(null, "UJCMS 11 全新发布", HASH, new HashSet<>()));
    }

    @Test
    void mintingIsDeterministicAndCollisionSuffixed() {
        Set<String> used = new HashSet<>();
        assertEquals("a-hello", SeedOriginIds.mintArticle(null, "Hello", HASH, used));
        assertEquals("a-hello-2", SeedOriginIds.mintArticle(null, "Hello", HASH, used));
        assertEquals("a-hello-3", SeedOriginIds.mintArticle(null, "Hello", HASH, used));
        // 已保留的第 1/2 类 originId 先占位，铸造避让
        Set<String> reserved = new HashSet<>(Set.of("news"));
        assertEquals("news-2", SeedOriginIds.mintAliased("news", "c-", HASH, reserved));
    }

    @Test
    void longBasesAreTruncatedWithinSchemaLimit() {
        String longAlias = "x".repeat(200);
        String minted = SeedOriginIds.mintAliased(longAlias, "c-", HASH, new HashSet<>());
        assertTrue(minted.length() <= 128, "originId 须在种子架构上限 128 内: " + minted.length());
    }

    @Test
    void classificationFollowsOriginTriple() {
        String ours = "theme:demo";
        assertEquals(SeedOriginClass.OPERATOR_NEW, SeedOriginClass.classify(null, ours, null, "h1"));
        assertEquals(SeedOriginClass.FOREIGN_ORIGIN, SeedOriginClass.classify("theme:other", ours, "h1", "h1"));
        assertEquals(SeedOriginClass.ORIGIN_UNMODIFIED, SeedOriginClass.classify(ours, ours, "h1", "h1"));
        assertEquals(SeedOriginClass.ORIGIN_MODIFIED, SeedOriginClass.classify(ours, ours, "h1", "h2"));
        // 散列缺失（早于 R4 的导入）视作未改——与导入侧同一口径，宁可少报
        assertEquals(SeedOriginClass.ORIGIN_UNMODIFIED, SeedOriginClass.classify(ours, ours, null, "h1"));
        assertTrue(SeedOriginClass.ORIGIN_MODIFIED.keepsOriginId());
        assertTrue(!SeedOriginClass.FOREIGN_ORIGIN.keepsOriginId());
    }
}
