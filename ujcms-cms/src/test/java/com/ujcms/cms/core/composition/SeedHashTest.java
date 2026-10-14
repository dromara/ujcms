package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Placement;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 种子来源散列测试（R4）。<b>跨版本稳定性是契约</b>：规范化规则（键排序、紧凑序列化）或投影口径一改，
 * 全部存量 origin_hash_ 行会被误判为「操作员已改」——本测试用钉死的期望散列值把契约锁住，
 * 改动散列算法必须显式改动本测试并在决策日志记录数据影响。
 */
class SeedHashTest {

    @Test
    void canonicalizationIsKeyOrderInsensitive() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("title", "你好");
        a.put("count", 3);
        a.put("nested", new LinkedHashMap<>(Map.of("x", 1)));
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("nested", new LinkedHashMap<>(Map.of("x", 1)));
        b.put("count", 3);
        b.put("title", "你好");
        assertEquals(SeedHash.sha256Canonical(a), SeedHash.sha256Canonical(b));
    }

    @Test
    void arrayOrderIsSignificant() {
        assertNotEquals(SeedHash.sha256Canonical(List.of("a", "b")), SeedHash.sha256Canonical(List.of("b", "a")));
    }

    @Test
    void canonicalHashIsPinnedAcrossVersions() {
        // 钉死值：{"a":1,"b":[true,null,"文"]} 规范化后的 SHA-256。散列算法一改此值即变——
        // 破坏存量 origin_hash_ 的比对，须记录决策再改。
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("b", java.util.Arrays.asList(true, null, "文"));
        value.put("a", 1);
        // 独立验证：sha256(UTF-8('{"a":1,"b":[true,null,"文"]}'))
        assertEquals("59aa05c1316a38e0a389888108b71d3e885d93db1c54b0f84e0ece6a52eb3cff",
                SeedHash.sha256Canonical(value));
    }

    @Test
    void placementHashCoversPanelSettingsAndEnabled() {
        // Phase D 投影重置（ssr-reduction P6 再去 track）：面板投影为
        // {pageRef, slotName, panelShape, settings?, enabled}
        // （退役的 sourceKind/source/sectionData/track 不再入散列）。导入时刻的 bean（服务层保存后的
        // 最终状态）与「自库行重解析」产出相同散列：settings 经 disassemble 序列化落库、
        // 再经 getSettings() 解析——散列必须不变
        Placement placement = new Placement();
        placement.setPageRef("home");
        placement.setSlotName("focus");
        placement.setPanelShape("HeroCarousel");
        placement.setSettings(new LinkedHashMap<>(Map.of("title", "焦点", "limit", 6)));
        placement.setEnabled(true);
        String hash = SeedHash.placementHash(placement);

        placement.disassemble();
        Placement reloaded = new Placement();
        reloaded.setPageRef("home");
        reloaded.setSlotName("focus");
        reloaded.setPanelShape("HeroCarousel");
        reloaded.setSettingsJson(placement.getSettingsJson());
        reloaded.setEnabled(true);
        assertEquals(hash, SeedHash.placementHash(reloaded));

        reloaded.setEnabled(false);
        assertNotEquals(hash, SeedHash.placementHash(reloaded));
    }

    @Test
    void placementHashIgnoresEmptySettingsBlock() {
        // 设置块仅在非空时入投影：未设置与空映射同形
        Placement placement = new Placement();
        placement.setPageRef("home");
        placement.setSlotName("focus");
        placement.setPanelShape("HeroCarousel");
        placement.setEnabled(true);
        String hash = SeedHash.placementHash(placement);

        placement.setSettings(new LinkedHashMap<>());
        assertEquals(hash, SeedHash.placementHash(placement));

        placement.setSettings(new LinkedHashMap<>(Map.of("title", "焦点")));
        assertNotEquals(hash, SeedHash.placementHash(placement));
    }

    @Test
    void placementHashIncludesVisibilityOnlyWhenNonDefault() {
        // 显示设备（design-review P2-5）仅在非缺省时入投影：缺省 all 的投影键集与特性上线前相同，
        // 存量 origin_hash_ 依旧复现；desktop/mobile 计入且可自库行重导出
        Placement placement = new Placement();
        placement.setPageRef("home");
        placement.setSlotName("focus");
        placement.setPanelShape("HeroCarousel");
        placement.setEnabled(true);
        String hash = SeedHash.placementHash(placement);

        placement.setVisibility(Placement.VISIBILITY_ALL);
        assertEquals(hash, SeedHash.placementHash(placement));

        placement.setVisibility(Placement.VISIBILITY_DESKTOP);
        String desktopHash = SeedHash.placementHash(placement);
        assertNotEquals(hash, desktopHash);
        placement.setVisibility(Placement.VISIBILITY_MOBILE);
        assertNotEquals(desktopHash, SeedHash.placementHash(placement));
    }

    @Test
    void channelHashCoversSeedSettableFieldsAndDetectsEdits() {
        com.ujcms.cms.core.domain.Channel channel = new com.ujcms.cms.core.domain.Channel();
        channel.setSlug("news");
        channel.setName("新闻");
        channel.setSeoTitle("新闻");
        channel.setText("<p>单页正文</p>");
        String hash = SeedHash.channelHash(channel);

        // customs：null 与空映射同形（种子未声明 == 模型无自定义字段）
        channel.setCustoms(new LinkedHashMap<>());
        assertEquals(hash, SeedHash.channelHash(channel));

        channel.setName("改过的名称");
        assertNotEquals(hash, SeedHash.channelHash(channel));
    }

    @Test
    void channelHashAddsLinkFieldsOnlyWhenSet() {
        // D45 条件入键：targetBlank=false / linkUrl 空 与基线同形（存量散列不重置）；设置后入散列
        com.ujcms.cms.core.domain.Channel channel = new com.ujcms.cms.core.domain.Channel();
        channel.setSlug("message-board");
        channel.setName("留言");
        String base = SeedHash.channelHash(channel);
        channel.setTargetBlank(false);
        channel.setLinkUrl("");
        assertEquals(base, SeedHash.channelHash(channel));

        channel.setType(com.ujcms.cms.core.domain.Channel.TYPE_LINK);
        String asLink = SeedHash.channelHash(channel);
        assertNotEquals(base, asLink);
        channel.setLinkUrl("/message-board");
        String withUrl = SeedHash.channelHash(channel);
        assertNotEquals(asLink, withUrl);
        channel.setTargetBlank(true);
        assertNotEquals(withUrl, SeedHash.channelHash(channel));
    }

    @Test
    void articleHashCoversContentAndTagsNotLifecycle() {
        com.ujcms.cms.core.domain.Article article = new com.ujcms.cms.core.domain.Article();
        article.setTitle("标题");
        article.setText("<p>正文</p>");
        article.setImage("/uploads/theme-seed/1/t/img/a.svg");
        article.setAuthor("作者");
        String hash = SeedHash.articleHash(article, List.of("示例"));

        // status / publishDate 属生命周期，不入散列
        article.setStatus(com.ujcms.cms.core.domain.Article.STATUS_ARCHIVED);
        article.setPublishDate(java.time.OffsetDateTime.now().minusDays(30));
        assertEquals(hash, SeedHash.articleHash(article, List.of("示例")));

        assertNotEquals(hash, SeedHash.articleHash(article, List.of("示例", "新增标签")));
        article.setTitle("改过的标题");
        assertNotEquals(hash, SeedHash.articleHash(article, List.of("示例")));
    }

    @Test
    void articleHashCoversMediaExtFieldsAndImageList() {
        // A6 投影扩展（散列重置已在决策日志记录）：媒体扩展字段入散列；图片集空列表与未设置同形
        com.ujcms.cms.core.domain.Article article = new com.ujcms.cms.core.domain.Article();
        article.setTitle("标题");
        String base = SeedHash.articleHash(article, List.of());
        article.setImageList(List.of());
        assertEquals(base, SeedHash.articleHash(article, List.of()));

        article.setVideo("/uploads/theme-seed/1/t/media/demo.mp4");
        String withVideo = SeedHash.articleHash(article, List.of());
        assertNotEquals(base, withVideo);
        article.setFile("/uploads/theme-seed/1/t/media/sample.zip");
        assertNotEquals(withVideo, SeedHash.articleHash(article, List.of()));

        com.ujcms.cms.core.domain.Article.ArticleImage image =
                new com.ujcms.cms.core.domain.Article.ArticleImage();
        image.setUrl("/uploads/theme-seed/1/t/img/p1.svg");
        image.setName("图1");
        article.setFile(null);
        article.setImageList(List.of(image));
        assertNotEquals(withVideo, SeedHash.articleHash(article, List.of()));
    }

    @Test
    void canonicalWriterWritesBigDecimalPlainAndNormalisesScale() {
        // 数值型自定义字段（价格 42800）：树节点工厂剥尾零后须以普通记法写出，不得落成 4.28E+4；
        // 42800 / 42800.0 / 4.28E+4 归一为同一字节序列与同一散列
        java.util.Map<String, Object> a = java.util.Map.of("price", new java.math.BigDecimal("42800"));
        java.util.Map<String, Object> b = java.util.Map.of("price", new java.math.BigDecimal("42800.0"));
        java.util.Map<String, Object> c = java.util.Map.of("price", new java.math.BigDecimal("4.28E+4"));
        String json = new String(SeedExportBundle.canonicalJsonBytes(a), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(json.contains("\"price\": 42800"), json);
        assertEquals(json, new String(SeedExportBundle.canonicalJsonBytes(b), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(SeedHash.sha256Canonical(a), SeedHash.sha256Canonical(b));
        assertEquals(SeedHash.sha256Canonical(a), SeedHash.sha256Canonical(c));
        java.util.Map<String, Object> d = java.util.Map.of("price", new java.math.BigDecimal("9688.50"));
        assertTrue(new String(SeedExportBundle.canonicalJsonBytes(d), java.nio.charset.StandardCharsets.UTF_8)
                .contains("\"price\": 9688.5"));
    }

    @Test
    void articleHashAddsFileListOnlyWhenNonEmpty() {
        // D45 条件入键：附件列表空列表与未设置同形；有条目即入散列
        com.ujcms.cms.core.domain.Article article = new com.ujcms.cms.core.domain.Article();
        article.setTitle("标题");
        String base = SeedHash.articleHash(article, List.of());
        article.setFileList(List.of());
        assertEquals(base, SeedHash.articleHash(article, List.of()));

        com.ujcms.cms.core.domain.Article.ArticleFile file = new com.ujcms.cms.core.domain.Article.ArticleFile();
        file.setUrl("/uploads/theme-seed/1/t/media/sample.zip");
        file.setName("示例.zip");
        file.setLength(215L);
        article.setFileList(List.of(file));
        String withFile = SeedHash.articleHash(article, List.of());
        assertNotEquals(base, withFile);
        file.setName("改名.zip");
        article.setFileList(List.of(file));
        assertNotEquals(withFile, SeedHash.articleHash(article, List.of()));
    }

    @Test
    void topicHashCoversContentNotArticleBindings() {
        com.ujcms.cms.core.domain.Topic topic = new com.ujcms.cms.core.domain.Topic();
        topic.setSlug("featured");
        topic.setTitle("精选");
        topic.setImage("/uploads/x.svg");
        String hash = SeedHash.topicHash(topic);
        assertEquals(hash, SeedHash.topicHash(topic));

        topic.setTitle("改过的专题");
        assertNotEquals(hash, SeedHash.topicHash(topic));
    }

    @Test
    void regionHashNormalizesPanelIdsToStructure() {
        // 面板ID是每次导入分配的雪花ID，不入散列；行ID/行布局/每列面板数入散列
        RegionLayout a = new RegionLayout(List.of(new RegionLayout.Row("r1", "halves",
                List.of(new RegionLayout.Column(List.of("111", "222")), new RegionLayout.Column(List.of("333"))))));
        RegionLayout b = new RegionLayout(List.of(new RegionLayout.Row("r1", "halves",
                List.of(new RegionLayout.Column(List.of("999", "888")), new RegionLayout.Column(List.of("777"))))));
        assertEquals(SeedHash.regionHash(a), SeedHash.regionHash(b));

        RegionLayout c = new RegionLayout(List.of(new RegionLayout.Row("r1", "thirds",
                List.of(new RegionLayout.Column(List.of("111", "222")), new RegionLayout.Column(List.of("333"))))));
        assertNotEquals(SeedHash.regionHash(a), SeedHash.regionHash(c));
    }
}
