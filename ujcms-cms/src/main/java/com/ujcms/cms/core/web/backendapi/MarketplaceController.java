package com.ujcms.cms.core.web.backendapi;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.component.ThemeInstallReport;
import com.ujcms.cms.core.composition.SeedOverwrite;
import com.ujcms.cms.core.marketplace.MarketplaceClient;
import com.ujcms.cms.core.marketplace.MarketplaceClient.Asset;
import com.ujcms.cms.core.marketplace.MarketplaceClient.CatalogItemDetail;
import com.ujcms.cms.core.marketplace.MarketplaceClient.CatalogItemPage;
import com.ujcms.cms.core.marketplace.MarketplaceClient.ItemVersion;
import com.ujcms.cms.core.marketplace.MarketplaceClient.UpdateCheckEntry;
import com.ujcms.cms.core.marketplace.MarketplaceClient.UpdateCheckResult;
import com.ujcms.cms.core.marketplace.MarketplaceConnectionService;
import com.ujcms.cms.core.marketplace.MarketplaceConnectionService.ConnectionStatus;
import com.ujcms.cms.core.marketplace.MarketplaceConnectionService.DeviceStart;
import com.ujcms.cms.core.marketplace.MarketplaceConnectionService.DisconnectResult;
import com.ujcms.cms.core.marketplace.MarketplaceConnectionService.EntitlementsSnapshot;
import com.ujcms.cms.core.marketplace.MarketplaceConnectionService.PollResult;
import com.ujcms.cms.core.marketplace.MarketplaceInstallService;
import com.ujcms.cms.core.marketplace.MarketplaceUnavailableException;
import com.ujcms.cms.core.service.ThemeInstallService.ActivateTarget;
import com.ujcms.cms.core.service.ThemeInstallService.SeedScope;
import com.ujcms.cms.core.service.ThemeInstallService.UpdateReport;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.common.web.exception.Http400Exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * 应用市场 admin API（remote-marketplace C1）。控制台唯一的商店通道——目录浏览走本代理，
 * 安装在服务端完成（D-K3），控制台永不跨源直连。端点：
 * <ul>
 *   <li>{@code GET  /api/backend/core/marketplace/state}                 — 启用状态 + 运行版本（兼容角标数据源，D-C1-6）</li>
 *   <li>{@code GET  /api/backend/core/marketplace/items}                 — 目录列表（可用性信封）</li>
 *   <li>{@code GET  /api/backend/core/marketplace/items/{slug}}          — 条目详情（信封）</li>
 *   <li>{@code GET  /api/backend/core/marketplace/items/{slug}/versions} — 版本列表（信封）</li>
 *   <li>{@code GET  /api/backend/core/marketplace/asset?path=…}          — 资产代理（预览图/截图，D-C1-4）</li>
 *   <li>{@code POST /api/backend/core/marketplace/install}               — 下载 + sha256 校验 + 既有安装管线</li>
 * </ul>
 *
 * <p>目录读取的降级契约（D-C1-3）：商店不可达/限流不报错，应答
 * {@code {available:false, reason, retryAfterSeconds}}，控制台呈现离线提示；
 * {@code ujcms.marketplace.enabled=false} 时 reason 为 {@code disabled}（控制台隐藏页签，
 * 其余一切不受影响）。安装失败按 {@code marketplace.error.*} i18n 键报 400。
 *
 * @author PONY
 */
@RestController("backendMarketplaceController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/marketplace")
public class MarketplaceController {
    private final MarketplaceClient client;
    private final MarketplaceInstallService installService;
    private final MarketplaceConnectionService connectionService;
    private final Props props;
    /** 店面站点地址（购买深链 D-K5 的基址；缺省随 base-url——生产环境店面与 API 同域） */
    private final String storefrontUrl;

    public MarketplaceController(MarketplaceClient client, MarketplaceInstallService installService,
                                 MarketplaceConnectionService connectionService, Props props,
                                 @org.springframework.beans.factory.annotation.Value(
                                         "${ujcms.marketplace.storefront-url:${ujcms.marketplace.base-url:https://marketplace.ujcms.com}}")
                                 String storefrontUrl) {
        this.client = client;
        this.installService = installService;
        this.connectionService = connectionService;
        this.props = props;
        this.storefrontUrl = storefrontUrl.endsWith("/")
                ? storefrontUrl.substring(0, storefrontUrl.length() - 1) : storefrontUrl;
    }

    /**
     * 市场启用状态 + 后端运行版本 + 店面地址。版本供控制台算 {@code minUjcmsVersion} 兼容角标
     * （仅提示；权威门槛在安装器）；店面地址供控制台拼购买深链 {@code {storefrontUrl}/items/{slug}}
     * （D-K5：购买只在店面网站发生，路径约定与店面 M7 对齐）。
     */
    @GetMapping("state")
    @PreAuthorize("hasAnyAuthority('marketplace:browse','*')")
    public StateResult state() {
        return new StateResult(client.isEnabled(), props.getVersion(), storefrontUrl);
    }

    @GetMapping("items")
    @PreAuthorize("hasAnyAuthority('marketplace:browse','*')")
    public StoreResult<CatalogItemPage> items(@RequestParam(required = false) @Nullable String q,
                                              @RequestParam(required = false) @Nullable String kind,
                                              @RequestParam(required = false) @Nullable String tag,
                                              @RequestParam(required = false) @Nullable String sort,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int pageSize) {
        return guarded(() -> client.listItems(q, kind, tag, sort, page, Math.min(pageSize, 100)));
    }

    @GetMapping("items/{slug}")
    @PreAuthorize("hasAnyAuthority('marketplace:browse','*')")
    public StoreResult<CatalogItemDetail> item(@PathVariable String slug) {
        return guarded(() -> client.getItem(slug));
    }

    @GetMapping("items/{slug}/versions")
    @PreAuthorize("hasAnyAuthority('marketplace:browse','*')")
    public StoreResult<List<ItemVersion>> versions(@PathVariable String slug) {
        return guarded(() -> client.listVersions(slug));
    }

    /**
     * 资产代理（D-C1-4）：{@code path} 是目录响应携带的不透明相对引用（仅 {@code /files/items/**}）。
     * 控制台经带鉴权的 blob 请求加载图片，浏览器按 Cache-Control 缓存；市场不可达时应答 404，
     * 控制台回退占位图。
     */
    @GetMapping("asset")
    @PreAuthorize("hasAnyAuthority('marketplace:browse','*')")
    public ResponseEntity<byte[]> asset(@RequestParam @NotBlank String path) {
        if (!client.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        Asset asset;
        try {
            asset = client.fetchAsset(path);
        } catch (MarketplaceUnavailableException e) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        if (asset.contentType() != null) {
            headers.setContentType(MediaType.parseMediaType(asset.contentType()));
        }
        headers.setCacheControl(asset.cacheControl() != null ? asset.cacheControl()
                : CacheControl.maxAge(java.time.Duration.ofDays(1)).cachePublic().getHeaderValue());
        return new ResponseEntity<>(asset.bytes(), headers, org.springframework.http.HttpStatus.OK);
    }

    /**
     * 从市场安装（C1 免费；C2 起连接账号后付费条目按授权放行）：下载授权 → 临时文件 →
     * sha256 校验（D-K7）→ 既有 {@code ThemeInstallService} 安装管线（含种子三态，与上传安装
     * 同报告形状——控制台复用同一套安装后 UX）。
     */
    @PostMapping("install")
    @PreAuthorize("hasAnyAuthority('marketplace:install','*')")
    @OperationLog(module = "marketplace", operation = "install", type = OperationType.CREATE)
    public ThemeInstallReport install(@RequestBody @Valid InstallParams params) {
        String themeName = params.themeName() == null || params.themeName().isBlank()
                ? params.slug() : params.themeName().trim();
        return installService.install(params.slug(), params.version(), params.siteId(), themeName,
                params.activate(), parseSeedScope(params.seedScope()));
    }

    /**
     * 更新已装主题到市场新版本（C2）：同一条授权/下载/校验链，落到既有更新管线
     * （暂存校验 + 原子替换 + origin_hash_ 种子三态差异报告，与上传更新同报告形状）。
     */
    @PostMapping("update")
    @PreAuthorize("hasAnyAuthority('marketplace:install','*')")
    @OperationLog(module = "marketplace", operation = "update", type = OperationType.UPDATE)
    public UpdateReport update(@RequestBody @Valid UpdateParams params) {
        String themeName = params.themeName() == null || params.themeName().isBlank()
                ? params.slug() : params.themeName().trim();
        return installService.update(params.slug(), params.version(), params.siteId(), themeName,
                parseSeedScope(params.seedScope()), parseOverwrite(params.overwrite()));
    }

    /**
     * 更新检查（C2 画廊角标）：控制台送已装主题 名称/版本 对（市场安装的目录名缺省即条目
     * slug——服务端按 slug 匹配），逐条应答是否有兼容新版本。遥测边界：本调用只携带
     * 主题名与版本号（C3 记录该决定），不缓存。
     */
    @PostMapping("update-check")
    @PreAuthorize("hasAnyAuthority('marketplace:browse','*')")
    public StoreResult<List<UpdateCheckResult>> updateCheck(@RequestBody List<@Valid UpdateCheckEntry> entries) {
        if (entries.size() > 100) {
            throw new Http400Exception("update-check entries must not exceed 100");
        }
        return guarded(() -> client.updateCheck(entries));
    }

    // ----- 账号连接（C2：设备授权流，marketplace:connect 与浏览/安装分权） -----

    /** 连接状态（控制台连接芯片；只读投影，绝不含令牌） */
    @GetMapping("connection")
    @PreAuthorize("hasAnyAuthority('marketplace:browse','*')")
    public ConnectionStatus connection() {
        return connectionService.getStatus();
    }

    /**
     * 发起设备授权流（RFC 8628 / D-K4）：应答 user_code + 批准页链接（含二维码素材
     * verification_uri_complete）供连接对话框展示；device_code 只留服务端。商店不可达
     * 走可用性信封（对话框呈离线提示）。
     */
    @PostMapping("connection/start")
    @PreAuthorize("hasAnyAuthority('marketplace:connect','*')")
    @OperationLog(module = "marketplace", operation = "connect", type = OperationType.CREATE)
    public StoreResult<DeviceStart> connectionStart(@RequestBody(required = false) @Nullable StartParams params) {
        return guarded(() -> connectionService.startConnect(params == null ? null : params.instanceName()));
    }

    /**
     * 轮询连接结果。控制台可任意频率调用——真正打到市场令牌端点的频率由服务端通告的
     * {@code interval} 节流（{@code slow_down} 间隔 +5s）；批准成功即持久化连接（加密落库，
     * D-C1-1）并返回账号信息。
     */
    @PostMapping("connection/poll")
    @PreAuthorize("hasAnyAuthority('marketplace:connect','*')")
    public PollResult connectionPoll() {
        return connectionService.pollConnect();
    }

    /** 断开连接：尽力远程吊销 refresh token，无论成败本地连接立即清除 */
    @PostMapping("connection/disconnect")
    @PreAuthorize("hasAnyAuthority('marketplace:connect','*')")
    @OperationLog(module = "marketplace", operation = "disconnect", type = OperationType.DELETE)
    public DisconnectResult connectionDisconnect() {
        return connectionService.disconnect();
    }

    /**
     * 连接账号的有效授权（C2：塑形付费条目 UI——已购解锁安装，未购走购买深链 D-K5）。
     * 未连接返回空快照；连接失效以 {@code reconnectRequired=true} 呈现（浏览面不报错）。
     */
    @GetMapping("entitlements")
    @PreAuthorize("hasAnyAuthority('marketplace:browse','*')")
    public StoreResult<EntitlementsSnapshot> entitlements() {
        return guarded(connectionService::listEntitlements);
    }

    private <T> StoreResult<T> guarded(Supplier<T> loader) {
        if (!client.isEnabled()) {
            return new StoreResult<>(false, "disabled", null, null);
        }
        try {
            return new StoreResult<>(true, null, null, loader.get());
        } catch (MarketplaceUnavailableException e) {
            return new StoreResult<>(false, e.getReason().getValue(), e.getRetryAfterSeconds(), null);
        }
    }

    /** 表单/JSON 的 seedScope 解析（与 ThemeController 同规则：小写 {@code none|composition|all}，未知 400） */
    private static SeedScope parseSeedScope(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return SeedScope.NONE;
        }
        try {
            return SeedScope.parse(value);
        } catch (IllegalArgumentException e) {
            throw new Http400Exception("seedScope must be none|composition|all: " + value);
        }
    }

    /** overwrite 解析（§6.1：{@code fillOnly|syncUnmodified|resetAllOrigin}，缺省 fillOnly——从不覆盖操作员行） */
    private static SeedOverwrite parseOverwrite(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return SeedOverwrite.FILL_ONLY;
        }
        try {
            return SeedOverwrite.parse(value);
        } catch (IllegalArgumentException e) {
            throw new Http400Exception(e.getMessage());
        }
    }

    /** 市场安装请求体（themeName 缺省取 slug；activate 装后激活槽位 desktop|mobile|both，缺省只安装；seedScope 三态小写线值） */
    public record InstallParams(@NotBlank String slug, @Nullable String version, @NotNull Long siteId,
                                @Nullable String themeName, @Nullable ActivateTarget activate,
                                @Nullable String seedScope) {
    }

    /** 市场更新请求体（C2：themeName 为已装主题目录名，缺省取 slug；overwrite 缺省 fillOnly） */
    public record UpdateParams(@NotBlank String slug, @Nullable String version, @NotNull Long siteId,
                               @Nullable String themeName, @Nullable String seedScope,
                               @Nullable String overwrite) {
    }

    /** 连接发起请求体（实例名在批准页展示；缺省「UJCMS {版本}」） */
    public record StartParams(@Nullable String instanceName) {
    }

    /** 市场启用状态 + 后端运行版本（D-C1-6）+ 店面地址（购买深链基址，D-K5） */
    public record StateResult(boolean enabled, String backendVersion, String storefrontUrl) {
    }

    /** 目录读取的可用性信封（D-C1-3）：available=false 时 data 为空，reason 为 offline|rate-limited|disabled */
    public record StoreResult<T>(boolean available, @Nullable String reason,
                                 @Nullable Integer retryAfterSeconds, @Nullable T data) {
    }
}
