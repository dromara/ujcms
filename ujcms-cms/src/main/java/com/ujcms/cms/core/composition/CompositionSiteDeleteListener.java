package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.PageRegionMapper;
import com.ujcms.cms.core.mapper.PageRegionSnapshotMapper;
import com.ujcms.cms.core.mapper.PlacementMapper;
import com.ujcms.cms.core.mapper.ThemeStateMapper;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.common.file.FileHandler;
import com.ujcms.common.web.PathResolver;
import org.springframework.stereotype.Component;

/**
 * 站点删除时的页面构成级联清理（docs/spec/site-delete-cascade/plan.md）。
 *
 * <p>构成四表（区域 / 投放 / 发布快照 / 主题状态）外键 RESTRICT、由服务层负责清理
 * （11.x-composition 变更集约定）；行皆站点辖域，站点删除即整体清除，无守卫、无删除前快照
 * （区域历史恢复端点按站点坐标门禁，站点没了快照永不可达）。种子资源目录
 * {@code /theme-seed/{siteId}/} 一并删除——与站点删除既有的模板/上传目录清理同型。
 * 收束一处而非分散在各构成 Service：级联决策单点可见，缓存也只作废一次。
 *
 * @author PONY
 */
@Component
public class CompositionSiteDeleteListener implements SiteDeleteListener {
    private final PageRegionMapper regionMapper;
    private final PageRegionSnapshotMapper snapshotMapper;
    private final PlacementMapper placementMapper;
    private final ThemeStateMapper themeStateMapper;
    private final CompositionCacheSync cacheSync;
    private final ConfigService configService;
    private final PathResolver pathResolver;

    public CompositionSiteDeleteListener(PageRegionMapper regionMapper, PageRegionSnapshotMapper snapshotMapper,
                                         PlacementMapper placementMapper, ThemeStateMapper themeStateMapper,
                                         CompositionCacheSync cacheSync, ConfigService configService,
                                         PathResolver pathResolver) {
        this.regionMapper = regionMapper;
        this.snapshotMapper = snapshotMapper;
        this.placementMapper = placementMapper;
        this.themeStateMapper = themeStateMapper;
        this.cacheSync = cacheSync;
        this.configService = configService;
        this.pathResolver = pathResolver;
    }

    @Override
    public void preSiteDelete(Long siteId) {
        snapshotMapper.deleteBySiteId(siteId);
        placementMapper.deleteBySiteId(siteId);
        regionMapper.deleteBySiteId(siteId);
        themeStateMapper.deleteBySiteId(siteId);
        FileHandler uploadHandler = configService.getUnique().getUploadStorage().getFileHandler(pathResolver);
        uploadHandler.deleteDirectory(SeedAssetCopier.THEME_SEED_DIR + siteId);
        cacheSync.invalidateAll("site-delete");
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }
}
