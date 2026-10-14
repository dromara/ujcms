package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Placement;
import com.ujcms.cms.core.mapper.PlacementMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.exception.Http400Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 面板投放服务测试（component-settings Phase D 后的存量职责）：面板保存的组件/轨道校验、
 * 归属防线与未知ID重建。渲染期解析在 {@code PanelResolverTest}。
 */
class PlacementServiceTest {
    private static final Long SITE_ID = 1L;
    private static final String PAGE = "home";

    private PlacementMapper placementMapper;
    private PlacementService service;

    @BeforeEach
    void setUp() {
        placementMapper = mock(PlacementMapper.class);
        PanelResolveCache resolveCache =
                new PanelResolveCache(new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), 60);
        // cache-sync 关闭戳记（syncEnabled=false）：单测无数据库；本地作废语义与生产一致
        CompositionCacheSync cacheSync = new CompositionCacheSync(resolveCache,
                mock(com.ujcms.cms.core.mapper.CacheStampMapper.class), false,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        service = new PlacementService(placementMapper, cacheSync, new SnowflakeSequence(0, 0));
    }

    private static PageManifest.SlotDef region() {
        return new PageManifest.SlotDef(false, List.of("PromoCards"), List.of("full"), null, null);
    }

    private Placement panelBean(long id, String component) {
        Placement bean = new Placement();
        bean.setId(id);
        bean.setSiteId(SITE_ID);
        bean.setPageRef(PAGE);
        bean.setSlotName("body");
        bean.setPanelShape(component);
        bean.setSettings(new HashMap<>(Map.of("title", "快捷入口")));
        bean.setEnabled(true);
        return bean;
    }

    @Test
    void savePanelAllowsManyPerRegionAndValidatesComponent() {
        Placement p1 = panelBean(0L, "PromoCards");
        Placement p2 = panelBean(0L, "PromoCards");
        service.savePanel(p1, region());
        service.savePanel(p2, region());
        // 面板多对一共享区域名：两次新增都直接 insert，无唯一键点查
        verify(placementMapper, times(2)).insert(any());

        // 组件不在区域 allowedShapes 内 → 400
        assertThrows(Http400Exception.class,
                () -> service.savePanel(panelBean(0L, "Banner"), region()));
    }

    @Test
    void savePanelSerializesSettingsJson() {
        Placement fresh = panelBean(0L, "PromoCards");
        service.savePanel(fresh, region());
        ArgumentCaptor<Placement> captor = ArgumentCaptor.forClass(Placement.class);
        verify(placementMapper).insert(captor.capture());
        assertNotNull(captor.getValue().getSettingsJson());
    }

    @Test
    void validateChecksShapeAndVisibilityWithoutWriting() {
        // 写时复制跳过写库的冻结面板仍经此复验（D20）：Shape 不在区域 allowedShapes / 显示设备非三值 → 400
        assertThrows(Http400Exception.class, () -> service.validate(panelBean(0L, "Banner"), region()));
        Placement badVisibility = panelBean(0L, "PromoCards");
        badVisibility.setVisibility("tv");
        assertThrows(Http400Exception.class, () -> service.validate(badVisibility, region()));
        service.validate(panelBean(0L, "PromoCards"), region());
        verify(placementMapper, never()).insert(any());
        verify(placementMapper, never()).update(any());
    }

    @Test
    void savePanelGuardsOwnershipAndRecreatesUnknownId() {
        // (a) id 指向他站的面板行 → 400（归属校验：草稿树的客户端 id 不得改写他站投放）
        Placement foreign = panelBean(7001L, "PromoCards");
        foreign.setSiteId(99L);
        when(placementMapper.select(7001L)).thenReturn(foreign);
        assertThrows(Http400Exception.class,
                () -> service.savePanel(panelBean(7001L, "PromoCards"), region()));

        // (b) id 指向非面板行（panel_shape_ 为空的历史残留）→ 400
        Placement nonPanel = panelBean(7002L, "PromoCards");
        nonPanel.setPanelShape(null);
        when(placementMapper.select(7002L)).thenReturn(nonPanel);
        assertThrows(Http400Exception.class,
                () -> service.savePanel(panelBean(7002L, "PromoCards"), region()));

        // (c) id 不存在（面板已被此前保存的孤立清理硬删——D8 撤销「删除面板/行」的快照重放）→
        //     按负载配置重建为新投放（新雪花ID、insert），而非 400
        when(placementMapper.select(7003L)).thenReturn(null);
        Long recreated = service.savePanel(panelBean(7003L, "PromoCards"), region());
        assertNotNull(recreated);
        assertNotEquals(7003L, recreated);
        verify(placementMapper, never()).update(any());

        // (d) 归属相符 → 更新成功，且保留既有行的种子来源（面板编辑不清洗 provenance）
        Placement owned = panelBean(7004L, "PromoCards");
        owned.setOrigin("theme:mytheme");
        owned.setOriginId("home#body#shared#r1#0#0");
        when(placementMapper.select(7004L)).thenReturn(owned);
        Placement incoming = panelBean(7004L, "PromoCards");
        service.savePanel(incoming, region());
        ArgumentCaptor<Placement> captor = ArgumentCaptor.forClass(Placement.class);
        verify(placementMapper).update(captor.capture());
        assertEquals("theme:mytheme", captor.getValue().getOrigin());
    }
}
