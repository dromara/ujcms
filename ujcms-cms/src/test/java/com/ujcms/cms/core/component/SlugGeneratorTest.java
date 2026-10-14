package com.ujcms.cms.core.component;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.mapper.SiteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SlugGenerator} 单元测试：slugify → 拼音回退（按站点设置）→ ID 回退 → 冲突消解。
 *
 * @author PONY
 */
class SlugGeneratorTest {
    private static final long SITE_ID = 1L;
    private static final int MAX = SlugGenerator.ARTICLE_MAX_LENGTH;

    private SiteMapper siteMapper;
    private Site site;
    private SlugGenerator generator;

    @BeforeEach
    void setUp() {
        siteMapper = mock(SiteMapper.class);
        site = new Site();
        site.setId(SITE_ID);
        when(siteMapper.select(SITE_ID)).thenReturn(site);
        generator = new SlugGenerator(siteMapper);
    }

    private void enablePinyin() {
        Site.Permalink permalink = new Site.Permalink();
        permalink.setPinyin(true);
        site.setPermalink(permalink);
    }

    @Test
    void asciiTitleBecomesSlugWithoutConsultingSite() {
        assertEquals("hello-world", generator.generate(SITE_ID, "Hello World", 99L, MAX, s -> false));
        // slugify 已得非空结果时不读站点设置
        verify(siteMapper, never()).select(any());
    }

    @Test
    void collisionAppendsIncrementingSuffix() {
        Set<String> taken = Set.of("hello-world", "hello-world-2");
        assertEquals("hello-world-3", generator.generate(SITE_ID, "Hello World", 99L, MAX, taken::contains));
        assertEquals("hello-world-2", generator.generate(SITE_ID, "Hello World", 99L, MAX, "hello-world"::equals));
    }

    @Test
    void cjkTitleWithPinyinOffFallsBackToId() {
        assertEquals("99", generator.generate(SITE_ID, "中国", 99L, MAX, s -> false));
        assertEquals("", generator.suggest(SITE_ID, "中国", MAX, s -> false));
    }

    @Test
    void cjkTitleWithPinyinOnBecomesPinyinSlug() {
        enablePinyin();
        assertEquals("zhong-guo", generator.generate(SITE_ID, "中国", 99L, MAX, s -> false));
        assertEquals("zhong-guo", generator.suggest(SITE_ID, "中国", MAX, s -> false));
    }

    @Test
    void pinyinFallbackIsCappedAtMaxSyllables() {
        enablePinyin();
        // 15 个汉字 → 仅取前 PINYIN_MAX_SYLLABLES（12）个音节
        String slug = generator.generate(SITE_ID, "一二三四五六七八九十百千万亿零", 99L, MAX, s -> false);
        assertEquals(SlugGenerator.PINYIN_MAX_SYLLABLES, slug.split("-").length, slug);
        assertTrue(slug.startsWith("yi-er-san-si-wu-"), slug);
        assertFalse(slug.endsWith("ling"), slug);
    }

    @Test
    void pinyinOnlyConsultedWhenSlugifyIsEmpty() {
        enablePinyin();
        // 标题含 ASCII 部分：直接 slugify，不做拼音转写
        assertEquals("ujcms-13", generator.generate(SITE_ID, "UJCMS 13 全新发布", 99L, MAX, s -> false));
        verify(siteMapper, never()).select(any());
    }

    @Test
    void unknownSiteWithCjkTitleFallsBackToId() {
        when(siteMapper.select(SITE_ID)).thenReturn(null);
        assertEquals("7", generator.generate(SITE_ID, "中国", 7L, MAX, s -> false));
    }

    @Test
    void blankTitleFallsBackToId() {
        assertEquals("42", generator.generate(SITE_ID, null, 42L, MAX, s -> false));
        assertEquals("42", generator.generate(SITE_ID, "   ", 42L, MAX, s -> false));
        assertEquals("", generator.suggest(SITE_ID, null, MAX, s -> false));
    }

    @Test
    void toPinyinKeepsNonChineseCharacters() {
        String pinyin = SlugGenerator.toPinyin("UJCMS 中国 13", SlugGenerator.PINYIN_MAX_SYLLABLES);
        assertTrue(pinyin.contains("UJCMS"), pinyin);
        assertTrue(pinyin.contains("13"), pinyin);
        assertTrue(pinyin.contains("ZHONG"), pinyin);
        assertTrue(pinyin.contains("GUO"), pinyin);
        // 音节上限：超出部分丢弃
        assertEquals(" ZHONG  GUO ", SlugGenerator.toPinyin("中国人", 2));
    }

    @Test
    void lengthCapIsRespectedWithSuffix() {
        // 基串恰好占满上限；冲突后缀从上限中扣除
        assertEquals("abcdefgh-2", generator.generate(SITE_ID, "abcdefghij", 5L, 10, "abcdefghij"::equals));
        assertEquals(10, generator.generate(SITE_ID, "abcdefghij", 5L, 10, "abcdefghij"::equals).length());
        // 基串超长：先截断（优先连字符处），再消解冲突
        assertEquals("alpha-beta", generator.generate(SITE_ID, "alpha beta gamma", 5L, 12, s -> false));
        String unique = generator.unique("alpha-beta", 12, Set.of("alpha-beta", "alpha-beta-2")::contains);
        assertEquals("alpha-beta-3", unique);
        assertTrue(unique.length() <= 12);
    }

    // ---- 站点行锁（D22）：生成别名先锁住站点行，再查重；建议（控制台预览）不锁

    @Test
    void generateLocksTheSiteRowBeforeLookingUpSlugs() {
        List<String> looked = new ArrayList<>();
        generator.generate(SITE_ID, "hello", 5L, MAX, s -> {
            looked.add(s);
            return false;
        });
        InOrder inOrder = inOrder(siteMapper);
        inOrder.verify(siteMapper).lockById(SITE_ID);
        assertEquals(List.of("hello"), looked);
        // 建议只是预览：不锁
        generator.suggest(SITE_ID, "hello", MAX, s -> false);
        verify(siteMapper, times(1)).lockById(SITE_ID);
    }
}
