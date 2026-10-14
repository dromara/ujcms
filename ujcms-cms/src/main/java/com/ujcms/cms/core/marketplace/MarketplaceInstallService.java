package com.ujcms.cms.core.marketplace;

import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ujcms.cms.core.component.ThemeInstallReport;
import com.ujcms.cms.core.composition.SeedOverwrite;
import com.ujcms.cms.core.domain.ThemePackage;
import com.ujcms.cms.core.marketplace.MarketplaceClient.DownloadGrant;
import com.ujcms.cms.core.marketplace.MarketplaceClient.DownloadedPackage;
import com.ujcms.cms.core.service.ThemeInstallService;
import com.ujcms.cms.core.service.ThemeInstallService.ActivateTarget;
import com.ujcms.cms.core.service.ThemeInstallService.SeedScope;
import com.ujcms.cms.core.service.ThemeInstallService.UpdateReport;
import com.ujcms.cms.core.service.ThemePackageService;

/**
 * 从应用市场安装/更新主题（install-flow §6 P3 起写穿主题包库）：包解析次序为
 * 库命中（slug+版本，或授权散列匹配）→ 申请下载授权 → 下载到临时文件 → 按授权校验
 * sha256（D-K7，不合即拒）→ <b>入库</b>（{@link ThemePackageService#save}，取代旧
 * 临时文件用毕即删）→ 从库内副本交给既有 {@link ThemeInstallService} 管线（安装走
 * install(Path…)，更新走 update(Path…)——校验 / 解包或原子替换 / 构成门槛 / 种子生命周期
 * （origin_hash_ 三态差异）原样生效，kickoff C1 §3 / D-C1-5）。
 *
 * <p>零重传（§6）：指定版本且库内命中（含文件在场）时完全不与市场交互——同一包装到
 * 第二个站点离线也可完成；未指定版本时授权响应携带权威散列，散列命中同样免下载。
 * 库命中路径不检查 {@code marketplace.enabled}（包已在本地，装它不是市场行为）。
 *
 * <p>C2：账号已连接时下载授权携带 Bearer 令牌——付费条目按账号授权放行（D-K6），免费条目的
 * 下载日志归因到账号与实例；连接失效退回匿名授权（免费条目不因连接故障装不上；付费条目由
 * 市场按匿名拒绝，控制台的重连提示负责引导操作员）。
 *
 * <p>商店不可达在此转译为可诉的 {@code marketplace.error.*} 400（安装是显式动作，报错即降级；
 * 目录浏览的降级走控制器信封，D-C1-3）。
 */
@Service
public class MarketplaceInstallService {
    private static final Logger logger = LoggerFactory.getLogger(MarketplaceInstallService.class);

    private final MarketplaceClient client;
    private final MarketplaceConnectionService connectionService;
    private final ThemeInstallService themeInstallService;
    private final ThemePackageService packageService;

    public MarketplaceInstallService(MarketplaceClient client, MarketplaceConnectionService connectionService,
                                     ThemeInstallService themeInstallService, ThemePackageService packageService) {
        this.client = client;
        this.connectionService = connectionService;
        this.themeInstallService = themeInstallService;
        this.packageService = packageService;
    }

    /**
     * @param slug      市场条目标识
     * @param version   指定版本（{@code null} 取最新发布版）
     * @param themeName 安装到站点的主题目录名（控制台缺省取 slug）
     * @param activate  装后激活到的槽位（桌面 / 手机 / 两者）；{@code null} = 只安装
     */
    public ThemeInstallReport install(String slug, @Nullable String version, Long siteId, String themeName,
                                      @Nullable ActivateTarget activate, SeedScope seedScope) {
        ThemePackage pkg = resolvePackage(slug, version);
        ThemeInstallReport report = packageService.withPackageFile(pkg, path ->
                themeInstallService.install(path, siteId, themeName, activate, seedScope));
        logger.info("Marketplace theme installed: item={} package={}@{} site={} name={} sha256={}",
                slug, pkg.getName(), pkg.getVersion(), siteId, themeName, report.getSha256());
        return report;
    }

    /**
     * 更新已装主题到市场新版本（C2）：同一条库解析/授权/下载/校验链，落到
     * {@link ThemeInstallService#update} 的暂存校验 + 原子替换 + 种子差异
     * （added/seedChanged/operatorModified，origin_hash_ 报告原样）。
     *
     * @param themeName 已装主题目录名（更新目标；市场安装缺省即 slug）
     */
    public UpdateReport update(String slug, @Nullable String version, Long siteId, String themeName,
                               SeedScope seedScope, SeedOverwrite overwrite) {
        ThemePackage pkg = resolvePackage(slug, version);
        UpdateReport report = packageService.withPackageFile(pkg, path ->
                themeInstallService.update(path, siteId, themeName, seedScope, overwrite));
        logger.info("Marketplace theme updated: item={} package={}@{} site={} name={} sha256={}",
                slug, pkg.getName(), pkg.getVersion(), siteId, themeName, report.sha256());
        return report;
    }

    /**
     * 包解析：库命中（零重传）或 授权 → 下载 → sha256 校验（D-K7）→ 入库。
     */
    private ThemePackage resolvePackage(String slug, @Nullable String version) {
        if (version != null) {
            ThemePackage hit = packageService.findBySlugAndVersion(slug, version);
            if (hit != null && packageService.packageFileExists(hit)) {
                logger.info("Marketplace package resolved from library (no transfer): item={} version={}",
                        slug, version);
                return hit;
            }
        }
        if (!client.isEnabled()) {
            throw new MarketplaceException("marketplace.error.disabled",
                    "Marketplace is disabled (ujcms.marketplace.enabled=false)");
        }
        DownloadGrant grant;
        try {
            grant = requestGrant(slug, version);
        } catch (MarketplaceUnavailableException e) {
            throw asInstallError(e);
        }
        // 未指定版本：授权响应携带权威散列——散列命中即免下载（最新版已在库）
        ThemePackage bySha = packageService.findBySlugAndSha256(slug, grant.sha256());
        if (bySha != null && packageService.packageFileExists(bySha)) {
            logger.info("Marketplace package resolved from library by grant sha256 (no transfer): item={} sha256={}",
                    slug, grant.sha256());
            return bySha;
        }
        DownloadedPackage pkg;
        try {
            pkg = client.downloadPackage(grant);
        } catch (MarketplaceUnavailableException e) {
            throw asInstallError(e);
        }
        try {
            // D-K7：授权散列 vs 落盘字节散列，不合即拒——绝不把可疑包收入库/交给安装器
            if (!pkg.sha256().equalsIgnoreCase(grant.sha256())) {
                logger.warn("Marketplace package sha256 mismatch: item={} version={} grant={} downloaded={}",
                        slug, version, grant.sha256(), pkg.sha256());
                throw new MarketplaceException("marketplace.error.sha256Mismatch",
                        "Downloaded package digest " + pkg.sha256()
                                + " does not match the download grant digest " + grant.sha256());
            }
            return packageService.save(pkg.file(), ThemePackage.SOURCE_MARKETPLACE, slug, slug);
        } finally {
            FileUtils.deleteQuietly(pkg.file().toFile());
        }
    }

    /**
     * 下载授权（C2）：已连接则带令牌（透明刷新 + 401 重试在 {@code withAccessToken}）；
     * 连接失效（须重连）退回匿名授权，不让免费安装陪葬。
     */
    private DownloadGrant requestGrant(String slug, @Nullable String version) {
        if (connectionService.isConnected()) {
            try {
                return connectionService.withAccessToken(token -> client.requestDownload(slug, version, token));
            } catch (MarketplaceException e) {
                String code = e.getMessage();
                if (!"marketplace.error.reconnectRequired".equals(code)
                        && !"marketplace.error.notConnected".equals(code)) {
                    throw e;
                }
                logger.warn("Marketplace connection unusable for download grant; falling back to anonymous: {}",
                        e.getDetail());
            }
        }
        return client.requestDownload(slug, version, null);
    }

    private MarketplaceException asInstallError(MarketplaceUnavailableException e) {
        if (e.getReason() == MarketplaceUnavailableException.Reason.RATE_LIMITED) {
            return new MarketplaceException("marketplace.error.rateLimited", e.getMessage(), e);
        }
        return new MarketplaceException("marketplace.error.offline", e.getMessage(), e);
    }
}
