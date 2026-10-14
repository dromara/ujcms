package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.composition.ThemeSeedOrchestrator.SeedRunResult;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.ThemePackage;
import com.ujcms.cms.core.marketplace.MarketplaceClient;
import com.ujcms.cms.core.service.InstallService;
import com.ujcms.cms.core.service.InstallService.InstallState;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.ThemeInstallService;
import com.ujcms.cms.core.service.ThemePackageService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.security.Secures;
import com.ujcms.common.web.exception.Http401Exception;
import com.ujcms.common.web.exception.Http404Exception;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.Length;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.ujcms.cms.core.support.UrlConstants.API;

/**
 * 安装接口（install-flow §5）。控制台安装向导的后端；仅在安装状态非 INSTALLED 时开放——
 * 安全过滤链按状态放行（{@code SecurityConfig}），此处再运行时双重校验；完成翻转后原子
 * 关闭。除 {@code state} 外的全部端点必须携带一次性安装令牌（请求头
 * {@code ujcms-install-token}，D12——引导时生成、明文见启动日志）。
 *
 * <p>响应一律为 {@code *Result} record（响应白名单约定）；密码经客户端 SM2 加密传输，
 * 与登录同路径（D2）。
 *
 * @author PONY
 */
@Tag(name = "安装接口")
@RestController
@RequestMapping(API + "/install")
public class InstallController {
    /**
     * 一次性安装令牌请求头（与 {@code ujcms-site-id} 命名风格一致）
     */
    public static final String TOKEN_HEADER = "ujcms-install-token";

    private final InstallService installService;
    private final SiteService siteService;
    private final ThemeInstallService themeInstallService;
    private final ThemePackageService themePackageService;
    private final MarketplaceClient marketplaceClient;
    private final Props props;

    public InstallController(InstallService installService, SiteService siteService,
                             ThemeInstallService themeInstallService, ThemePackageService themePackageService,
                             MarketplaceClient marketplaceClient, Props props) {
        this.installService = installService;
        this.siteService = siteService;
        this.themeInstallService = themeInstallService;
        this.themePackageService = themePackageService;
        this.marketplaceClient = marketplaceClient;
        this.props = props;
    }

    /**
     * 安装状态与向导预填。无需令牌（仅暴露安装状态、站点名、请求推导的网络参数预填与
     * 令牌文件路径——D49：路径本身不是秘密，令牌证明的是读取该文件的能力）。
     * 已安装后随其余安装端点一并在安全过滤链层关闭（401）——控制台的"安装页还是登录页"
     * 路由判定走匿名 {@code /api/env/backend-state} 的 {@code installed} 字段，不依赖本端点。
     * 网络参数按当前请求预填并尊重 {@code X-Forwarded-*}（D3：预填 + 可编辑，不做纯自动探测）。
     */
    @GetMapping("/state")
    public StateResult state(HttpServletRequest request) {
        InstallState state = installService.getState();
        if (state == InstallState.INSTALLED) {
            return new StateResult(state.name(), true, null, null, null, null);
        }
        Site site = siteService.select(InstallService.INIT_SITE_ID);
        String siteName = site != null ? site.getName() : null;
        return new StateResult(state.name(), false, siteName, prefill(request),
                installService.checkTemplateStorage(), installService.getTokenFilePath());
    }

    /**
     * 校验安装令牌（向导第一步）。
     */
    @PostMapping("/verify-token")
    public TokenResult verifyToken(@RequestHeader(value = TOKEN_HEADER, required = false) @Nullable String token) {
        guardState();
        guardToken(token);
        return new TokenResult(true);
    }

    /**
     * 配置站点 1 名称与网络参数。全部字段可缺省——缺省即保留当前值；提供域名时端口缺省
     * 表示清除（80/443）。幂等，可重复提交。
     */
    @PostMapping("/site")
    public StepResult site(@RequestHeader(value = TOKEN_HEADER, required = false) @Nullable String token,
                           @RequestBody @Valid SiteParams params) {
        guardState();
        guardToken(token);
        installService.configureSite1(params.name, params.protocol, params.domain, params.port);
        return new StepResult(true);
    }

    /**
     * 配置管理员凭据。密码为客户端 SM2 加密密文（与登录同路径）；设置密码默认清除
     * 首次登录强制改密标记（C6），除非 {@code mustChangePassword=true}。幂等。
     */
    @PostMapping("/admin")
    public StepResult admin(@RequestHeader(value = TOKEN_HEADER, required = false) @Nullable String token,
                            @RequestBody @Valid AdminParams params) {
        guardState();
        guardToken(token);
        String password = StringUtils.isNotBlank(params.password)
                ? Secures.sm2Decrypt(params.password, props.getClientSm2PrivateKey())
                : null;
        installService.configureAdmin(params.username, password, params.mustChangePassword);
        return new StepResult(true);
    }

    /**
     * 向导主题步骤可选项（D30）：站点 1 当前主题与内置可用性、包库存量、市场开关。
     * 与其余步骤同一令牌门。
     */
    @GetMapping("/theme-options")
    public ThemeOptionsResult themeOptions(
            @RequestHeader(value = TOKEN_HEADER, required = false) @Nullable String token) {
        guardState();
        guardToken(token);
        Site site = siteService.select(InstallService.INIT_SITE_ID);
        String currentTheme = site != null ? site.getTheme() : "default";
        boolean bundledAvailable = site != null
                && (themeInstallService.themeDir(site.getId(), currentTheme).exists()
                || themePackageService.findUsableByName(currentTheme).isPresent());
        List<PackageItem> library = themePackageService.list().stream()
                .filter(themePackageService::packageFileExists)
                .map(pkg -> new PackageItem(pkg.getName(), pkg.getVersion(), pkg.getSource(), pkg.getSize()))
                .toList();
        return new ThemeOptionsResult(currentTheme, bundledAvailable, library, marketplaceClient.isEnabled());
    }

    /**
     * 主题步骤（§5/§7 / D30）：来源 {@code bundled}（缺省，站点 1 当前主题）/
     * {@code library}（{@code ref=名称[@版本]}）/ {@code marketplace}（{@code ref=slug[@版本]}），
     * 均经主题包库解析。{@code seedScope=all} 导入演示种子（来源三元组幂等，断点续装安全）；
     * {@code seedScope=none} 得到空站（Q5）。
     */
    @PostMapping("/theme")
    public ThemeResult theme(@RequestHeader(value = TOKEN_HEADER, required = false) @Nullable String token,
                             @RequestBody @Valid ThemeParams params) {
        guardState();
        guardToken(token);
        InstallService.ThemeStepResult result = installService.installTheme(InstallService.INIT_SITE_ID,
                params.source, params.ref, params.seedScope);
        return new ThemeResult(result.theme(), result.skipped(), result.message(), result.seed());
    }

    /**
     * 主题上传步骤（D30 {@code upload} 来源）：ZIP 先写穿主题包库（与库面板同口径，包内清单
     * 必须声明 {@code themeName}），再从库内副本安装到站点 1——一条管线，与
     * {@code library} 来源殊途同归。
     */
    @PostMapping("/theme-upload")
    public ThemeResult themeUpload(@RequestHeader(value = TOKEN_HEADER, required = false) @Nullable String token,
                                   @RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "seedScope", defaultValue = "all")
                                   @Pattern(regexp = "all|none") String seedScope) {
        guardState();
        guardToken(token);
        ThemePackage pkg = themePackageService.save(file, ThemePackage.SOURCE_UPLOAD, null);
        InstallService.ThemeStepResult result = installService.installTheme(InstallService.INIT_SITE_ID,
                "library", pkg.getName() + "@" + pkg.getVersion(), seedScope);
        return new ThemeResult(result.theme(), result.skipped(), result.message(), result.seed());
    }

    /**
     * 完成安装：守卫翻转 INSTALLED（并发/续装下唯一赢家），随后运行安装完成 seeder；
     * 翻转即关闭全部安装端点并作废令牌。
     */
    @PostMapping("/complete")
    public CompleteResult complete(@RequestHeader(value = TOKEN_HEADER, required = false) @Nullable String token) {
        guardState();
        guardToken(token);
        InstallService.CompleteResult result = installService.complete();
        return new CompleteResult(result.installed(), result.alreadyInstalled(), result.seeders(), result.warning());
    }

    /**
     * 运行时状态守卫（安全过滤链之外的双重校验）：已安装后一律 404——端点已关闭。
     */
    private void guardState() {
        if (installService.isInstalled()) {
            throw new Http404Exception("error.notFound");
        }
        if (installService.getState() == InstallState.NOT_INSTALLED) {
            throw new IllegalStateException("System core not ready: bootstrap has not run yet");
        }
    }

    private void guardToken(@Nullable String token) {
        if (!installService.verifyToken(token)) {
            throw new Http401Exception("Invalid install token");
        }
    }

    /**
     * 网络参数预填（D3）：优先 {@code X-Forwarded-Proto/Host/Port}，缺省回落当前请求。
     * <p>
     * {@code X-Forwarded-Host} 取第一个值；{@code [2001:db8::1]:8080} 形式按方括号拆分主机与端口（域名去方括号）；
     * 不带方括号的 IPv6 字面量（多个冒号）整体作为主机，端口取 {@code X-Forwarded-Port}。
     * 站点域名校验不接受 IPv6 字面量，运维仍需填写主机名，但端口不再被误解析。
     */
    static Prefill prefill(HttpServletRequest request) {
        String protocol = StringUtils.defaultIfBlank(request.getHeader("X-Forwarded-Proto"), request.getScheme());
        String forwardedHost = StringUtils.trimToNull(
                StringUtils.substringBefore(request.getHeader("X-Forwarded-Host"), ","));
        String host;
        Integer port;
        if (forwardedHost != null) {
            String portText = null;
            if (forwardedHost.startsWith("[") && forwardedHost.indexOf(']') > 0) {
                int close = forwardedHost.indexOf(']');
                String tail = forwardedHost.substring(close + 1);
                portText = tail.startsWith(":") ? tail.substring(1) : null;
                host = forwardedHost.substring(1, close);
            } else if (forwardedHost.indexOf(':') >= 0 && forwardedHost.indexOf(':') == forwardedHost.lastIndexOf(':')) {
                int colon = forwardedHost.indexOf(':');
                portText = forwardedHost.substring(colon + 1);
                host = forwardedHost.substring(0, colon);
            } else {
                host = forwardedHost;
            }
            port = portText != null ? parsePort(portText) : parsePort(request.getHeader("X-Forwarded-Port"));
        } else {
            host = request.getServerName();
            port = request.getServerPort();
        }
        if (port != null && (("http".equals(protocol) && port == 80) || ("https".equals(protocol) && port == 443))) {
            port = null;
        }
        return new Prefill(protocol, host, port);
    }

    @Nullable
    private static Integer parsePort(@Nullable String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static class SiteParams {
        @Nullable
        @Length(max = 50)
        public String name;
        @Nullable
        @Pattern(regexp = "https?")
        public String protocol;
        @Nullable
        @Length(max = 100)
        public String domain;
        @Nullable
        public Integer port;
    }

    public static class AdminParams {
        @Nullable
        @Length(max = 50)
        public String username;
        /**
         * 客户端 SM2 加密密文
         */
        @Nullable
        public String password;
        public boolean mustChangePassword = false;
    }

    public static class ThemeParams {
        /**
         * 主题来源（D30）：缺省 {@code bundled}（站点 1 当前主题；开发期授权目录已在则只种子）
         */
        @Nullable
        @Pattern(regexp = "bundled|library|marketplace")
        public String source;
        /**
         * 来源引用：{@code library} 为 {@code 名称[@版本]}；{@code marketplace} 为
         * {@code slug[@版本]}；{@code bundled} 可缺省
         */
        @Nullable
        @Length(max = 120)
        public String ref;
        @Pattern(regexp = "all|none")
        public String seedScope = InstallService.SEED_SCOPE_ALL;
    }

    /**
     * 安装状态。{@code installed=true} 后其余字段为 null（端点即将关闭，仅供控制台路由）。
     * {@code tokenFile}：令牌文件绝对路径（D49），预设令牌或写入失败时为 null。
     */
    public record StateResult(String state, boolean installed, @Nullable String siteName,
                              @Nullable Prefill prefill, @Nullable String templateStorageWarning,
                              @Nullable String tokenFile) {
    }

    /**
     * 网络参数预填（D3：预填 + 可编辑）
     */
    public record Prefill(String protocol, String domain, @Nullable Integer port) {
    }

    public record TokenResult(boolean valid) {
    }

    public record StepResult(boolean ok) {
    }

    /**
     * 主题步骤响应
     *
     * @param seed 种子导入结构化结果（内容按类别计数 + 构成计数 + 失败明细），未导入种子时为 null
     */
    public record ThemeResult(String theme, boolean skipped, String message,
                              @Nullable SeedRunResult seed) {
    }

    /**
     * 向导主题步骤可选项（D30）
     *
     * @param currentTheme     站点 1 当前主题名（bundled 来源的安装目标）
     * @param bundledAvailable 内置来源可用（授权目录或库内同名包在场）
     * @param library          包库存量（仅文件在场的行）
     * @param marketplace      市场客户端是否启用（slug@版本 安装可用）
     */
    public record ThemeOptionsResult(String currentTheme, boolean bundledAvailable, List<PackageItem> library,
                                     boolean marketplace) {
    }

    /**
     * 包库条目（向导下拉）
     */
    public record PackageItem(String name, String version, String source, long size) {
    }

    public record CompleteResult(boolean installed, boolean alreadyInstalled, List<String> seeders,
                                 @Nullable String warning) {
    }
}
