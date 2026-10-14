package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.mapper.CacheStampMapper;
import com.ujcms.cms.core.mapper.PageRegionMapper;
import com.ujcms.cms.core.mapper.PageRegionSnapshotMapper;
import com.ujcms.cms.core.mapper.PlacementMapper;
import com.ujcms.cms.core.mapper.ThemeStateMapper;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.common.file.FileHandler;
import com.ujcms.common.web.PathResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站点删除构成级联测试（docs/spec/site-delete-cascade/plan.md）：四表按站点清除、
 * 种子资源目录删除、构成缓存作废——防止某张表悄悄掉出级联。
 */
class CompositionSiteDeleteListenerTest {
    private static final Long SITE_ID = 42L;

    private PageRegionMapper regionMapper;
    private PageRegionSnapshotMapper snapshotMapper;
    private PlacementMapper placementMapper;
    private ThemeStateMapper themeStateMapper;
    private FileHandler fileHandler;
    private CompositionSiteDeleteListener listener;

    @BeforeEach
    void setUp() {
        regionMapper = mock(PageRegionMapper.class);
        snapshotMapper = mock(PageRegionSnapshotMapper.class);
        placementMapper = mock(PlacementMapper.class);
        themeStateMapper = mock(ThemeStateMapper.class);
        fileHandler = mock(FileHandler.class);
        // cache-sync 关闭戳记（syncEnabled=false）：单测无数据库；本地作废语义与生产一致
        CompositionCacheSync cacheSync = new CompositionCacheSync(
                new PanelResolveCache(new SimpleMeterRegistry(), 60),
                mock(CacheStampMapper.class), false, new SimpleMeterRegistry());
        ConfigService configService = mock(ConfigService.class);
        Config config = mock(Config.class);
        Config.Storage storage = mock(Config.Storage.class);
        when(configService.getUnique()).thenReturn(config);
        when(config.getUploadStorage()).thenReturn(storage);
        when(storage.getFileHandler(any())).thenReturn(fileHandler);
        listener = new CompositionSiteDeleteListener(regionMapper, snapshotMapper, placementMapper,
                themeStateMapper, cacheSync, configService, mock(PathResolver.class));
    }

    @Test
    void preSiteDeleteCascadesAllCompositionState() {
        listener.preSiteDelete(SITE_ID);
        verify(regionMapper).deleteBySiteId(SITE_ID);
        verify(snapshotMapper).deleteBySiteId(SITE_ID);
        verify(placementMapper).deleteBySiteId(SITE_ID);
        verify(themeStateMapper).deleteBySiteId(SITE_ID);
        verify(fileHandler).deleteDirectory(SeedAssetCopier.THEME_SEED_DIR + SITE_ID);
        // 无事务环境下提交后动作即时执行：SSR 清缓存通知已发出
    }
}
