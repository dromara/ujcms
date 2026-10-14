package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.ThemeInstallService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.common.web.exception.Http404Exception;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * 站点解析器
 *
 * @author PONY
 */
public class SiteResolver {
    private static final Logger logger = LoggerFactory.getLogger(SiteResolver.class);
    private final SiteService siteService;
    private final ConfigService configService;
    private final ThemeInstallService themeInstallService;

    public SiteResolver(SiteService siteService, ConfigService configService,
                        ThemeInstallService themeInstallService) {
        this.siteService = siteService;
        this.configService = configService;
        this.themeInstallService = themeInstallService;
    }

    /**
     * 子目录前缀解析（multilingual P2 组合寻址）。查找顺序：
     * <ol>
     * <li>组合寻址 (host, subDir)——权威匹配，使 a.example.com/en 与 b.example.com/en 可共存；</li>
     * <li>遗留全局回退（仅子目录全局唯一时命中）：兼容存量部署 domain_ 为空/过期、以及 IP/localhost
     * 访问（Host 永不等于 domain_）——零数据迁移；</li>
     * <li>全部未命中→404（与原行为同状态码）。</li>
     * </ol>
     */
    public Site resolve(HttpServletRequest request, @Nullable String subDir) {
        if (StringUtils.isBlank(subDir)) {
            return resolve(request);
        }
        String host = request.getServerName();
        Site site = siteService.findByDomainAndSubDir(host, subDir);
        if (site == null) {
            site = siteService.findBySubDir(subDir);
        }
        if (site == null) {
            logger.warn("Site sub-dir not exist: {}", request.getRequestURL());
            throw new Http404Exception("error.siteSubDirNotExist", subDir);
        }
        return established(site);
    }

    /**
     * 按显式站点ID解析；未提供时按域名解析。
     * <p>
     * 生成实体的 {@code siteId} 默认值为 0（MessageBoard/Form/Disclosure/Mailbox 的 *Params 均继承实体），
     * 客户端省略该字段时这里收到的是 0 而非 null；站点ID恒为正数，故 0 及负数一律视为未提供，
     * 回退到域名解析，而不是报“站点ID不存在：0”。
     */
    public Site resolve(HttpServletRequest request, @Nullable Long siteId) {
        if (siteId != null && siteId > 0) {
            Site site = Optional.ofNullable(siteService.select(siteId)).orElseThrow(() -> {
                logger.warn("Site id not exist: {}", request.getRequestURL());
                return new Http404Exception("error.siteIdNotExist", String.valueOf(siteId));
            });
            return established(site);
        }
        return resolve(request);
    }

    public Site resolve(HttpServletRequest request) {
        String domain = request.getServerName();
        Site site = siteService.findByDomain(domain);
        if (site == null) {
            Long defaultSiteId = configService.getUnique().getDefaultSiteId();
            site = Optional.ofNullable(siteService.select(defaultSiteId))
                    .orElseThrow(() -> new IllegalStateException("Default site not found: " + defaultSiteId));
        }
        return established(site);
    }

    /**
     * 站点确定后的统一收口：置上下文、状态校验，以及主题预览覆盖（P2-1）的可激活性校验——
     * {@code FrontendInterceptor} 已按身份门槛置 {@code Contexts.findThemePreview()}，此处站点已知，
     * 候选主题名不合法 / 目录不存在 / 纯数据包 / 版本不兼容（{@code requireActivatable} 口径）
     * 一律 400，绝不静默回退活动主题渲染。
     */
    private Site established(Site site) {
        Contexts.setCurrentSite(site);
        if (site.isDisabled()) {
            throw new SiteDisabledException("error.siteDisabled", site.getName(), String.valueOf(site.getId()));
        }
        String themePreview = Contexts.findThemePreview();
        if (themePreview != null) {
            themeInstallService.requireThemePreviewable(site.getId(), themePreview);
        }
        return site;
    }
}
