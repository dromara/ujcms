package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.composition.PreviewUrlResolver;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.security.PreviewTokenService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.common.web.exception.Http400Exception;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;

/**
 * 页面构成 Controller（core 保留部分）：主题预览与构建器共用的预览令牌签发与代表性预览地址。
 * 页面目录、重扫、清缓存、健康报告与种子令牌修复自 2026-09-08 起归商业版 T3
 * {@code CompositionT3Controller}（页面可视化设计，URL 与权限码不变）。
 *
 * @author PONY
 */
@RestController("backendCompositionController")
@RequestMapping(BACKEND_API + "/core/composition")
public class CompositionController {
    private final PreviewTokenService previewTokenService;
    private final PreviewUrlResolver previewUrlResolver;

    public CompositionController(PreviewTokenService previewTokenService, PreviewUrlResolver previewUrlResolver) {
        this.previewTokenService = previewTokenService;
        this.previewUrlResolver = previewUrlResolver;
    }

    /**
     * 签发区域预览令牌（Phase 7 / §11.2 / §7.0）。构建器预览画布用它构造 iframe 的
     * {@code ?_preview=draft&_ptoken=} ——浏览器导航携带不了 Bearer 头，故以短时签发人隔离令牌过草稿门禁。
     * 主题画廊的预览（P2-1，{@code ?_theme=&_ptoken=}）同用此令牌，故 {@code theme:list} 同样放行——
     * 令牌只承载身份（草稿可见 + 主题试看均为只读渲染），可看主题者可试看。
     * 取 GET：签发是无状态签名、不落任何数据，演示站只读账号（{@code BackendInterceptor} 仅放行 GET）
     * 也要能加载预览画布；{@code /api} 响应一律 {@code no-store}，令牌不会被缓存复用。
     */
    @GetMapping("preview-token")
    @PreAuthorize("hasAnyAuthority('pageRegion:show','theme:list','*')")
    public PreviewTokenResult previewToken() {
        User user = Contexts.getCurrentUser();
        PreviewTokenService.Token token = previewTokenService.mint(user.getUsername());
        return new PreviewTokenResult(token.token(), token.expiresIn());
    }

    /**
     * 代表性预览地址（preview-url-resolver §4.1）：给定（页面, [变体], [栏目], [q]）产出可渲染
     * 该页面的候选 URL。构建器默认路径 / 路径选择器 / 范围自动寻址的数据源。与 {@code preview-token}
     * 同权限面（只读；构建器已同时使用两者），站点作用域取当前站点（与本控制器其余端点一致）。
     * {@code page} 与 {@code template} 二选一：{@code template} 按模板名反查（P3 模板列表预览按钮——
     * 内置固定路由表 + FormType 反查 + 清单正向绑定）。
     */
    @GetMapping("preview-urls")
    @PreAuthorize("hasAnyAuthority('pageRegion:show','theme:list','*')")
    public PreviewUrlResolver.Result previewUrls(@RequestParam(value = "page", required = false)
                                                 @Nullable String page,
                                                 @RequestParam(value = "template", required = false)
                                                 @Nullable String template,
                                                 @RequestParam(value = "variant", required = false)
                                                 @Nullable String variant,
                                                 @RequestParam(value = "channelId", required = false)
                                                 @Nullable Long channelId,
                                                 @RequestParam(value = "q", required = false) @Nullable String q,
                                                 @RequestParam(value = "limit", required = false)
                                                 @Nullable Integer limit) {
        Site site = Contexts.getCurrentSite();
        // 上限钳制 1..100，缺省 20
        int effectiveLimit = limit != null ? Math.max(1, Math.min(limit, 100)) : 20;
        String templateName = StringUtils.trimToNull(template);
        if (templateName != null) {
            return previewUrlResolver.resolveByTemplate(site, templateName, effectiveLimit);
        }
        if (StringUtils.isBlank(page)) {
            throw new Http400Exception("page or template is required");
        }
        return previewUrlResolver.resolve(site, page, StringUtils.trimToNull(variant),
                channelId, StringUtils.trimToNull(q), effectiveLimit);
    }

    /**
     * 预览令牌签发结果
     *
     * @param token     预览令牌（作为 {@code _ptoken} 传入前台预览 URL）
     * @param expiresIn 有效期（秒）
     */
    public record PreviewTokenResult(String token, long expiresIn) {
    }
}
