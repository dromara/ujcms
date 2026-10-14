package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Placement;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.ChannelMapper;
import com.ujcms.cms.core.mapper.PlacementMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 种子令牌修复器测试（theme-seed-export R1）：宽容展开 + savePanel 落库、来源散列仅对
 * 「修复前未改动」的行重落（等价于 D9 之后导入器产出）、悬空令牌逐面板跳过明细、
 * 区域插槽不可解析整面板跳过、无令牌/已归档面板不入范围。
 */
class SeedTokenRepairerTest {
    private static final Long SITE_ID = 1L;
    private static final String THEME_KEY = "1/default";
    private static final String ORIGIN = "theme:default";

    private PlacementService placementService;
    private PlacementMapper placementMapper;
    private ManifestService manifestService;
    private SeedTokenRepairer repairer;

    @BeforeEach
    void setUp() {
        placementService = mock(PlacementService.class);
        placementMapper = mock(PlacementMapper.class);
        manifestService = mock(ManifestService.class);
        SeedAssetCopier assetCopier = mock(SeedAssetCopier.class);
        SeedAssetCopier.Session session = mock(SeedAssetCopier.Session.class);
        // 资源会话桩：{{asset:}} 展开为确定性 URL（与真实会话同构；文件复制/附件登记不在本测范围）
        when(session.expand(any())).thenAnswer(inv -> {
            String value = inv.getArgument(0);
            return value == null ? null
                    : value.replace("{{asset:img/a.svg}}", "/uploads/theme-seed/1/default/img/a.svg");
        });
        when(assetCopier.open(anyLong(), anyString(), any(File.class), anyLong())).thenReturn(session);

        ChannelMapper channelMapper = mock(ChannelMapper.class);
        ArticleMapper articleMapper = mock(ArticleMapper.class);
        Channel video = new Channel();
        video.setId(42L);
        when(channelMapper.findBySiteIdAndSlug(SITE_ID, "video")).thenReturn(List.of(video));
        Article welcome = new Article();
        welcome.setId(77L);
        when(articleMapper.selectByOrigin(SITE_ID, ORIGIN, "a-welcome")).thenReturn(welcome);

        // 活动主题清单：article 页声明 aside 区域（VideoCards 允许）+ VideoCards 组件
        // （别名映射遗留形按字段定义驱动——修复器需解析 Shape 取设置字段定义）
        CompositionSchemas schemas = new CompositionSchemas();
        Shape videoCards = new ShapeLoader(schemas).load("""
                { "name": "VideoCards", "version": "1.0.0",
                  "settings": {
                    "channel": { "type": "channelRef" },
                    "other":   { "type": "channelRef" },
                    "article": { "type": "articleRef" },
                    "cover":   { "type": "media" },
                    "bg":      { "type": "text" } } }
                """, Shape.SOURCE_THEME_PREFIX + THEME_KEY, "x");
        PageManifest article = new ManifestLoader(schemas).load("""
                { "name": "article", "slots": {
                    "aside": { "kind": "region", "allowedShapes": ["VideoCards"] } } }
                """, "article.manifest.json");
        when(manifestService.getSnapshot()).thenReturn(RegistrySnapshot.builder()
                .addThemeShape(THEME_KEY, videoCards, "{}", "x")
                .addThemeManifest(THEME_KEY, article, "{}", "article.manifest.json").build());

        repairer = new SeedTokenRepairer(placementService, placementMapper, manifestService, assetCopier,
                channelMapper, articleMapper, mock(com.ujcms.cms.core.mapper.BlockMapper.class),
                mock(com.ujcms.cms.ext.mapper.VoteMapper.class));
    }

    private static Placement panel(long id, String page, Map<String, Object> settings) {
        Placement placement = new Placement();
        placement.setId(id);
        placement.setSiteId(SITE_ID);
        placement.setPageRef(page);
        placement.setSlotName("aside");
        placement.setPanelShape("VideoCards");
        placement.setSettings(new HashMap<>(settings));
        placement.setEnabled(true);
        placement.setOrigin(ORIGIN);
        return placement;
    }

    private SeedTokenRepairer.RepairReport repair() {
        return repairer.repair(SITE_ID, List.of(THEME_KEY), "default", "/templates",
                name -> new File("/nonexistent/" + name), 1L);
    }

    @Test
    void expandsTokensAndRestampsHashOnUnmodifiedRows() {
        Placement legacy = panel(10L, "article", Map.of(
                "channel", "{{channel:video}}",
                "article", "{{article:a-welcome}}",
                "cover", Map.of("url", "{{asset:img/a.svg}}"),
                "bg", "{{theme}}/assets/bg.png"));
        // 修复前散列复现（导入后未被操作员改动）→ 修复后重落散列（行等价于 D9 之后导入器产出）
        legacy.setOriginHash(SeedHash.placementHash(legacy));
        Placement clean = panel(11L, "article", Map.of("channel", "42"));
        Placement archived = panel(12L, "article", Map.of("channel", "{{channel:video}}"));
        archived.setArchivedAt(OffsetDateTime.now());
        when(placementService.listBySiteId(SITE_ID)).thenReturn(List.of(legacy, clean, archived));

        SeedTokenRepairer.RepairReport report = repair();

        assertEquals(1, report.panelsWithTokens());
        assertEquals(1, report.panelsRepaired());
        assertEquals(4, report.tokensExpanded());
        assertEquals(0, report.tokensSkipped());
        // 展开结果与导入语义逐字一致：引用 → 字符串ID；asset → 确定性 URL；{{theme}} → 来源主题前缀
        assertEquals("42", legacy.getSettings().get("channel"));
        assertEquals("77", legacy.getSettings().get("article"));
        assertEquals("/uploads/theme-seed/1/default/img/a.svg",
                ((Map<?, ?>) legacy.getSettings().get("cover")).get("url"));
        assertEquals("/templates/1/default/assets/bg.png", legacy.getSettings().get("bg"));
        verify(placementService).savePanel(eq(legacy), any());
        verify(placementMapper).updateOriginHash(10L, SeedHash.placementHash(legacy));
        // 无令牌/已归档面板不入范围
        verify(placementService, never()).savePanel(eq(clean), any());
        verify(placementService, never()).savePanel(eq(archived), any());
    }

    @Test
    void operatorModifiedRowsKeepStoredHash() {
        Placement modified = panel(20L, "article", Map.of("channel", "{{channel:video}}"));
        modified.setOriginHash("hash-of-original-seed-state");
        when(placementService.listBySiteId(SITE_ID)).thenReturn(List.of(modified));

        SeedTokenRepairer.RepairReport report = repair();

        // 修复照常保存，但不清洗脏标记（原散列保留——行仍是「操作员已改」）
        assertEquals(1, report.panelsRepaired());
        verify(placementService).savePanel(eq(modified), any());
        verify(placementMapper, never()).updateOriginHash(anyLong(), anyString());
    }

    @Test
    void unresolvableTokensSkippedWithDetail() {
        Placement dangling = panel(30L, "article", Map.of(
                "channel", "{{channel:ghost}}",
                "other", "{{channel:video}}"));
        when(placementService.listBySiteId(SITE_ID)).thenReturn(List.of(dangling));

        SeedTokenRepairer.RepairReport report = repair();

        // 可解析的照常展开保存；悬空令牌原样保留并逐面板列明
        assertEquals(1, report.panelsRepaired());
        assertEquals(1, report.tokensExpanded());
        assertEquals(1, report.tokensSkipped());
        assertEquals("42", dangling.getSettings().get("other"));
        assertEquals("{{channel:ghost}}", dangling.getSettings().get("channel"));
        SeedTokenRepairer.RepairReport.PanelRepair detail = report.panels().get(0);
        assertEquals(List.of("channel: {{channel:ghost}}"), detail.skipped());
        assertNull(detail.note());
    }

    @Test
    void aliasRefMapsExpandFieldDefDriven() {
        // 别名映射遗留形（D20：令牌文法之前的手写种子形）：channelRef 值 {"alias": "video"} → 站内ID
        Placement legacy = panel(50L, "article", Map.of(
                "channel", Map.of("alias", "video"),
                "other", Map.of("alias", "ghost")));
        legacy.setOriginHash(SeedHash.placementHash(legacy));
        when(placementService.listBySiteId(SITE_ID)).thenReturn(List.of(legacy));

        SeedTokenRepairer.RepairReport report = repair();

        assertEquals(1, report.panelsRepaired());
        assertEquals(1, report.tokensExpanded());
        assertEquals(1, report.tokensSkipped());
        assertEquals("42", legacy.getSettings().get("channel"));
        // 别名不可解析 → 原样保留（宽容语义）+ 跳过明细
        assertEquals(Map.of("alias", "ghost"), legacy.getSettings().get("other"));
        assertEquals(List.of("other: {\"alias\": \"ghost\"}"), report.panels().get(0).skipped());
        verify(placementService).savePanel(eq(legacy), any());
        verify(placementMapper).updateOriginHash(50L, SeedHash.placementHash(legacy));
    }

    @Test
    void panelSkippedWhenRegionSlotUndeclared() {
        Placement orphan = panel(40L, "ghostpage", Map.of("channel", "{{channel:video}}"));
        when(placementService.listBySiteId(SITE_ID)).thenReturn(List.of(orphan));

        SeedTokenRepairer.RepairReport report = repair();

        // 区域插槽不可解析：savePanel 无从校验，整面板跳过 + 注明原因
        assertEquals(0, report.panelsRepaired());
        assertEquals(1, report.tokensSkipped());
        verify(placementService, never()).savePanel(any(), any());
        SeedTokenRepairer.RepairReport.PanelRepair detail = report.panels().get(0);
        assertEquals(0, detail.expanded());
        assertTrue(detail.note() != null && detail.note().contains("区域插槽"));
    }
}
