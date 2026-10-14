package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.support.Contexts;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模板区域解析支撑（page-regions §7.4）。供 {@code <@region>} FreeMarker 指令使用：
 * 按 (站点, 活动主题, 渲染模板名) 解析页面清单中的区域插槽，再经
 * {@link RegionService#resolve} 取得行/列/面板结构与内容（主题声明的区域、主题命名空间）。
 *
 * <p>任何缺失都返回空区域——渲染期永不破坏页面（§9.3）。预览（草稿可见）沿用插槽的
 * {@link Contexts#isSlotPreview()} 标记（由 {@code FrontendInterceptor} 鉴权置位），属<b>进程内</b>解析，
 * 不经 HTTP 解析端点。
 *
 * @author PONY
 */
@Component
public class RegionTemplateSupport {
    private static final Logger logger = LoggerFactory.getLogger(RegionTemplateSupport.class);

    private final RegionService regionService;
    private final ManifestService manifestService;

    public RegionTemplateSupport(RegionService regionService, ManifestService manifestService) {
        this.regionService = regionService;
        this.manifestService = manifestService;
    }

    /**
     * 解析区域（无渲染上下文参数）。
     */
    public ResolvedRegion resolve(@Nullable Site site, @Nullable String templateName,
                                  @Nullable String pageOverride, String regionName) {
        return resolve(site, templateName, pageOverride, regionName, null, null, null);
    }

    /**
     * 解析区域，并把当前频道（如有）作为 {@code $ctx.*} 渲染上下文参数透传到面板来源解析（§14.2）；
     * 当前频道与活动布局变体同时构成范围覆盖的候选范围（visual-builder §9：{@code channel:<id>} →
     * {@code variant:<surface>:<value>} → 页面默认）。
     *
     * @param site         当前站点（渲染上下文缺失时为 {@code null} → 空区域）
     * @param templateName 未组装模板名（渲染上下文钩子写入；{@code page} 覆盖时可为 {@code null}）
     * @param pageOverride 页面名覆盖参数（边缘场景显式指定页面清单名）
     * @param regionName   区域插槽名
     * @param channelAlias 当前频道别名（频道页等渲染上下文携带；其它页面为 {@code null}）
     * @param channelId    当前频道ID（同上）
     * @param variant      活动布局变体（{@code <surface>:<value>}，前台控制器写入渲染模型
     *                     {@code layoutVariant}；无变体页面为 {@code null}）
     * @return 解析结果（永不为 {@code null}）
     */
    public ResolvedRegion resolve(@Nullable Site site, @Nullable String templateName, @Nullable String pageOverride,
                                  String regionName, @Nullable String channelAlias, @Nullable Long channelId,
                                  @Nullable String variant) {
        try {
            return doResolve(site, templateName, pageOverride, regionName, channelAlias, channelId, variant);
        } catch (RuntimeException e) {
            // §9.3 渲染期永不破坏页面的最终兜底
            logger.error("Region resolve failed, rendering empty (§9.3). region={}, template={}", regionName,
                    templateName, e);
            return ResolvedRegion.empty(regionName);
        }
    }

    private ResolvedRegion doResolve(@Nullable Site site, @Nullable String templateName, @Nullable String pageOverride,
                                     String regionName, @Nullable String channelAlias, @Nullable Long channelId,
                                     @Nullable String variant) {
        if (site == null) {
            logger.debug("Region tag: no site in render context, region={}", regionName);
            return ResolvedRegion.empty(regionName);
        }
        String themeKey = activeThemeKey(site);
        String pageRef = pageOverride;
        if (pageRef == null) {
            if (templateName == null) {
                logger.debug("Region tag: no template name in render context, region={}", regionName);
                return ResolvedRegion.empty(regionName);
            }
            PageManifest manifest = manifestService.getSnapshot()
                    .resolveThemeManifestByTemplate(themeKey, templateName);
            if (manifest == null) {
                logger.debug("Region tag: template '{}' bound by no manifest, theme={}, region={}",
                        templateName, themeKey, regionName);
                return ResolvedRegion.empty(regionName);
            }
            pageRef = manifest.getName();
        }
        Map<String, Object> ctxParams = buildCtxParams(site, channelAlias, channelId);
        // 区域预览模式（§11.2）：草稿可见；与插槽预览一致，由 FrontendInterceptor 鉴权置位。
        boolean preview = Contexts.isSlotPreview();
        // themeKey 用于主题清单的区域 SlotDef 与面板 Shape 命名空间。
        ResolveContext ctx = preview
                ? ResolveContext.templatePreview(themeKey, ctxParams)
                : ResolveContext.template(themeKey, ctxParams);
        return regionService.resolve(site.getId(), pageRef, regionName, ctx, preview, variant);
    }

    /**
     * 当前是否处于（区域）预览模式（§11.2）。
     */
    public static boolean isPreview() {
        return Contexts.isSlotPreview();
    }

    /**
     * 由可信的服务端对象构造 {@code $ctx.*} 白名单参数：{@code siteId} 恒有；频道页等携带频道时
     * 再加 {@code channel}（别名）与 {@code channelId}。
     */
    public static Map<String, Object> buildCtxParams(Site site, @Nullable String channelAlias,
                                                     @Nullable Long channelId) {
        Map<String, Object> params = new LinkedHashMap<>(4);
        params.put(CtxParams.SITE_ID, site.getId());
        if (channelAlias != null && !channelAlias.isBlank()) {
            params.put(CtxParams.CHANNEL, channelAlias);
        }
        if (channelId != null) {
            params.put(CtxParams.CHANNEL_ID, channelId);
        }
        return params;
    }

    /**
     * 当前请求的活动主题命名空间：移动端用 {@code mobileTheme}。
     * 与 {@code Site.assembleTemplate} 的主题键一致。
     */
    public static String activeThemeKey(Site site) {
        return Contexts.isMobile() ? site.getMobileThemeKey() : site.getThemeKey();
    }
}
