package com.ujcms.cms.core.service;

import com.ujcms.cms.core.component.ThemeInstallException;
import com.ujcms.cms.core.component.ThemeInstallReport;
import com.ujcms.cms.core.composition.ThemeSeedOrchestrator;
import com.ujcms.cms.core.composition.ThemeSeedOrchestrator.SeedRunResult;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.ThemePackage;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.domain.UserExt;
import com.ujcms.cms.core.domain.UserRole;
import com.ujcms.cms.core.mapper.ConfigMapper;
import com.ujcms.cms.core.mapper.UserExtMapper;
import com.ujcms.cms.core.mapper.UserMapper;
import com.ujcms.cms.core.mapper.UserRoleMapper;
import com.ujcms.cms.core.marketplace.MarketplaceInstallService;
import com.ujcms.cms.core.service.ThemeInstallService.ActivateTarget;
import com.ujcms.cms.core.service.args.RoleArgs;
import com.ujcms.cms.core.service.args.SiteArgs;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.FirstInitSeeder;
import com.ujcms.cms.core.support.InstallTokenFile;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.security.Secures;
import com.ujcms.common.security.oauth.QqOauthClient;
import com.ujcms.common.security.oauth.WeiboOauthClient;
import com.ujcms.common.security.oauth.WeixinOauthClient;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 安装引擎（install-flow §5）：既有服务调用之上的薄编排层，控制台向导与无人值守清单
 * （{@code ujcms.install.auto}）共用。站点 1 / 管理员 1 为引导骨架的固定 ID 行——安装只
 * <b>配置</b>它们，绝不创建（D4）。
 *
 * <p>状态机（§3 / D1）：{@code NOT_INSTALLED}（无 config 行）→ {@code CORE_READY}（引导
 * 数据脚本已跑，未经安装）→ {@code INSTALLED}。状态持久化在 {@code ujcms_config
 * .install_state_}，完成翻转是守卫单行 UPDATE——并发安装器与集群节点下原子。列为 NULL 的
 * config 行视同 CORE_READY（数据脚本与状态写入之间崩溃的续装窗口），绝不视同已安装。
 *
 * <p>每个步骤都是对既有行的幂等写：中断的安装停在 CORE_READY，向导/清单重跑即续装，
 * 不依赖任何内存状态（这替换了旧 {@code firstInit} 标志的崩溃丢种子缺陷）。
 *
 * <p>一次性安装令牌（D12）：引导时生成、SM3 摘要落库、明文打印日志（Q4）并写入
 * {@code WEB-INF/install-token.txt}（D49，{@link InstallTokenFile}），全部安装 API
 * 必带；完成翻转时清空作废、删除文件。已安装后 {@link #isInstalled()} 粘滞记忆，正常运行零查询。
 *
 * @author PONY
 */
@Service
public class InstallService {
    private static final Logger logger = LoggerFactory.getLogger(InstallService.class);

    /**
     * 引导骨架固定 ID：站点 1 / 管理员 1（D4）
     */
    public static final long INIT_SITE_ID = 1L;
    public static final long INIT_USER_ID = 1L;

    /**
     * 种子范围：全部导入（内容 + 构成）
     */
    public static final String SEED_SCOPE_ALL = "all";
    /**
     * 种子范围：跳过导入，得到空站（Q5，替代旧 {@code ujcms.data-seed-enabled=false}）
     */
    public static final String SEED_SCOPE_NONE = "none";
    /**
     * 清单 AI 服务商的固定代码（{@code Config.Ai.Provider.code}，控制台新增的服务商为随机 {@code p-xxxxxxxx}）：
     * 续装时据此原地更新，不重复追加
     */
    public static final String MANIFEST_AI_PROVIDER_CODE = "p-install";

    private static final String STATE_INSTALLED = "installed";

    private final ConfigMapper configMapper;
    private final ConfigService configService;
    private final SiteService siteService;
    private final UserService userService;
    private final RoleService roleService;
    private final UserMapper userMapper;
    private final UserExtMapper userExtMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final ThemeInstallService themeInstallService;
    private final ThemePackageService themePackageService;
    private final MarketplaceInstallService marketplaceInstallService;
    private final ThemeSeedOrchestrator seedOrchestrator;
    private final ArticleI18nService articleI18nService;
    private final ObjectProvider<InstallSitePort> sitePort;
    private final ObjectProvider<FirstInitSeeder> firstInitSeeders;
    private final FreeMarkerProperties freeMarkerProperties;
    private final InstallTokenFile tokenFile;

    /**
     * INSTALLED 粘滞记忆：状态机只前进，一旦观察到已安装便不再查库。安装期间（少量请求）
     * 每次调用查库且绕过二级缓存——集群另一节点完成翻转后，本节点下一次检查即收敛关闭。
     */
    private volatile boolean installedMemo = false;

    public InstallService(ConfigMapper configMapper, ConfigService configService, SiteService siteService,
                          UserService userService, RoleService roleService, UserMapper userMapper,
                          UserExtMapper userExtMapper, UserRoleMapper userRoleMapper,
                          PasswordEncoder passwordEncoder, ThemeInstallService themeInstallService,
                          ThemePackageService themePackageService,
                          MarketplaceInstallService marketplaceInstallService,
                          ThemeSeedOrchestrator seedOrchestrator, ArticleI18nService articleI18nService,
                          ObjectProvider<InstallSitePort> sitePort,
                          ObjectProvider<FirstInitSeeder> firstInitSeeders,
                          FreeMarkerProperties freeMarkerProperties, InstallTokenFile tokenFile) {
        this.configMapper = configMapper;
        this.configService = configService;
        this.siteService = siteService;
        this.userService = userService;
        this.roleService = roleService;
        this.userMapper = userMapper;
        this.userExtMapper = userExtMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.themeInstallService = themeInstallService;
        this.themePackageService = themePackageService;
        this.marketplaceInstallService = marketplaceInstallService;
        this.seedOrchestrator = seedOrchestrator;
        this.articleI18nService = articleI18nService;
        this.sitePort = sitePort;
        this.firstInitSeeders = firstInitSeeders;
        this.freeMarkerProperties = freeMarkerProperties;
        this.tokenFile = tokenFile;
    }

    /**
     * 安装状态（§3 / D1）
     */
    public enum InstallState {
        /**
         * 空库（无 config 行）——引导数据脚本尚未执行
         */
        NOT_INSTALLED,
        /**
         * 系统骨架就绪，待安装（含状态列为 NULL 的续装窗口行）
         */
        CORE_READY,
        /**
         * 安装完成，安装端点关闭
         */
        INSTALLED
    }

    public InstallState getState() {
        if (installedMemo) {
            return InstallState.INSTALLED;
        }
        String state = configMapper.selectInstallState();
        if (STATE_INSTALLED.equals(state)) {
            installedMemo = true;
            return InstallState.INSTALLED;
        }
        if (state != null || configMapper.countConfig() > 0) {
            return InstallState.CORE_READY;
        }
        return InstallState.NOT_INSTALLED;
    }

    public boolean isInstalled() {
        return getState() == InstallState.INSTALLED;
    }

    /**
     * 引导/重启时刷新一次性安装令牌（状态非 INSTALLED 时每次启动调用）：预设令牌
     * （{@code ujcms.install.token}）非空则使用其摘要，不打印明文也不写文件；否则随机生成，
     * 把明文打印到日志（Jenkins 模式，Q4）并写入令牌文件（D49）供看不到日志的环境读取。同时把状态归一为 {@code coreReady}（覆盖数据脚本与
     * 状态写入之间崩溃留下的 NULL 行）。崩溃重启后旧日志里的令牌即作废——以最后一次
     * 打印为准。
     *
     * @param presetToken 预设令牌，可为空
     */
    @Transactional(rollbackFor = Exception.class)
    public void refreshInstallToken(@Nullable String presetToken) {
        String token = StringUtils.isNotBlank(presetToken) ? presetToken : Secures.randomAlphanumeric(32);
        int rows = configMapper.updateInstallToken(Secures.sm3Hex(token));
        if (rows == 0) {
            // 守卫更新失败：系统已安装（他方并发完成），令牌不再需要
            return;
        }
        if (StringUtils.isNotBlank(presetToken)) {
            tokenFile.delete();
            logger.info("Install token preset via ujcms.install.token (not logged). "
                    + "System awaits installation at /cp/ or /api/install/**");
        } else {
            Path file = tokenFile.write(token);
            String fileHint = file != null ? "令牌同时写入文件（安装完成后自动删除）：" + file + "\n" : "";
            String line = "=".repeat(72);
            logger.warn("\n{}\n系统尚未安装。请打开控制台 /cp/ 完成安装，安装令牌（install token）：\n\n    {}\n\n"
                    + "{}令牌仅本次启动有效；重启会重新生成并再次打印。\n{}", line, token, fileHint, line);
        }
    }

    /**
     * 当前存在的令牌文件绝对路径（D49，向导第一步提示）；无文件返回 {@code null}。
     */
    public @Nullable String getTokenFilePath() {
        return tokenFile.existingPath();
    }

    /**
     * 删除残留的令牌文件（已安装系统启动时调用：集群另一节点完成安装、翻转后崩溃）。
     */
    public void discardTokenFile() {
        tokenFile.delete();
    }

    /**
     * 校验安装令牌（常数时间比较）。
     */
    public boolean verifyToken(@Nullable String token) {
        if (StringUtils.isBlank(token)) {
            return false;
        }
        String hash = configMapper.selectInstallTokenHash();
        if (hash == null) {
            return false;
        }
        return MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8),
                Secures.sm3Hex(token).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 配置站点 1 名称与网络参数（§5：{@code init-*} 从未有过的站点名写入 + 原
     * {@code initDomain()} 的同一组调用）。全部参数可空——空即保留当前值；提供域名时
     * 端口为空表示清除（80/443 缺省语义）。幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    public void configureSite1(@Nullable String name, @Nullable String protocol, @Nullable String domain,
                               @Nullable Integer port) {
        Site site = siteService.select(INIT_SITE_ID);
        if (site == null) {
            throw new IllegalStateException("Bootstrap site not found: " + INIT_SITE_ID);
        }
        if (StringUtils.isNotBlank(name)) {
            site.setName(name);
            siteService.update(site);
        }
        if (StringUtils.isNotBlank(domain)) {
            String effectiveProtocol = StringUtils.isNotBlank(protocol) ? protocol : site.getProtocol();
            siteService.updateDomain(INIT_SITE_ID, effectiveProtocol, domain);
            configService.updatePort(port);
        } else if (port != null) {
            configService.updatePort(port);
        }
    }

    /**
     * 清单首条配置站点 1（D4 / D44）：名称/网络参数同上；{@code lang}/{@code subDir} 非空即
     * 应用（缺省保留当前值——引导语言为 zh-CN），地址与语言组完整性经 {@code validateAddress}
     * 复验。{@code langOf} 对站点 1 不适用（引导主站点不能是别人的变体）——fail-loud，
     * 绝不静默忽略。控制台向导走上面的四参重载（向导无语言面）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void configureSite1(Props.Install.SiteEntry entry) {
        if (StringUtils.isNotBlank(entry.getLangOf())) {
            throw new IllegalStateException("Manifest first site entry configures bootstrap site 1 (D4) — "
                    + "langOf is not applicable (site 1 cannot be a language variant); declare variants as "
                    + "later entries referencing site 1 by name");
        }
        String lang = StringUtils.trimToNull(entry.getLang());
        String subDir = StringUtils.trimToNull(entry.getSubDir());
        validateLangFormat("site1", lang);
        validateSubDirFormat("site1", subDir);
        configureSite1(entry.getName(), entry.getProtocol(), entry.getDomain(), entry.getPort());
        if (lang == null && subDir == null) {
            return;
        }
        Site site = siteService.select(INIT_SITE_ID);
        if (site == null) {
            throw new IllegalStateException("Bootstrap site not found: " + INIT_SITE_ID);
        }
        boolean changed = false;
        if (lang != null && !lang.equals(site.getLang())) {
            site.setLang(lang);
            changed = true;
        }
        if (subDir != null && !subDir.equals(site.getSubDir())) {
            site.setSubDir(subDir);
            changed = true;
        }
        if (!changed) {
            return;
        }
        siteService.validateAddress(site, isMultiDomain());
        siteService.update(site);
    }

    /**
     * 配置管理员凭据（§5）。用户名/密码可空——空即保留当前值。设置密码时默认<b>清除</b>
     * {@code pwd_must_change_}（修复 docker {@code init-password} 缺口 C6），除非
     * {@code mustChangeNext} 显式要求保留。幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    public void configureAdmin(@Nullable String username, @Nullable String password, boolean mustChangeNext) {
        User user = userService.select(INIT_USER_ID);
        if (user == null) {
            throw new IllegalStateException("Bootstrap admin not found: " + INIT_USER_ID);
        }
        if (StringUtils.isNotBlank(username) && !username.equals(user.getUsername())) {
            user.setUsername(username);
            userService.update(user);
        }
        if (StringUtils.isNotBlank(password)) {
            userService.updatePassword(user, user.getExt(), password, mustChangeNext);
        }
    }

    /**
     * 清单注册开关步骤：写入“注册设置”的 {@code enabled}。缺省（{@code null}）不改动。幂等。
     *
     * @return 是否写入
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean configureRegister(@Nullable Boolean enabled) {
        if (enabled == null) {
            return false;
        }
        Config config = configService.getUnique();
        Config.Register register = config.getRegister();
        register.setEnabled(enabled);
        config.setRegister(register);
        configService.update(config);
        return true;
    }

    /**
     * 清单第三方登录步骤：把给出凭据的服务商写入“第三方登录”设置（{@link Config.Oauth}）并启用，未给出的服务商保持原样。
     * 应用ID与应用密钥须同时给出，只给其一 fail-loud（半套凭据装出的登录入口点了即报错）。幂等。
     * 级别判断（商业 T2 起）由调用方负责。
     *
     * @return 写入并启用的服务商代码，按 {@link Config.Oauth#PROVIDERS} 的顺序；清单未给出任何凭据时为空（不写库）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> configureOauth(Props.Install.Oauth manifest) {
        Map<String, Props.Install.OauthClient> entries = new LinkedHashMap<>();
        entries.put(WeixinOauthClient.PROVIDER_WEIXIN, manifest.getWeixin());
        entries.put(QqOauthClient.PROVIDER_QQ, manifest.getQq());
        entries.put(WeiboOauthClient.PROVIDER_WEIBO, manifest.getWeibo());

        Config config = configService.getUnique();
        Config.Oauth oauth = config.getOauth();
        List<String> configured = new ArrayList<>();
        for (Map.Entry<String, Props.Install.OauthClient> entry : entries.entrySet()) {
            String provider = entry.getKey();
            String clientId = entry.getValue().getClientId();
            String clientSecret = entry.getValue().getClientSecret();
            if (StringUtils.isAllBlank(clientId, clientSecret)) {
                continue;
            }
            if (StringUtils.isAnyBlank(clientId, clientSecret)) {
                throw new IllegalArgumentException("ujcms.install.oauth." + provider
                        + ": client-id and client-secret must be given together");
            }
            Config.Oauth.Client client = Objects.requireNonNull(oauth.client(provider));
            client.setClientId(clientId);
            client.setClientSecret(clientSecret);
            client.setEnabled(true);
            configured.add(provider);
        }
        if (configured.isEmpty()) {
            return configured;
        }
        config.setOauth(oauth);
        configService.update(config);
        return configured;
    }

    /**
     * 清单 AI 服务商步骤：把清单给出的服务商写入“AI服务商”设置（{@link Config.Ai}），设为默认服务商并打开全局开关。
     * 服务商以固定代码 {@link #MANIFEST_AI_PROVIDER_CODE} 落库，续装时原地更新；其余服务商、服务商分工与额度保持原样。
     * 接口地址与模型须同时给出，只给其一 fail-loud（缺一项的服务商每次调用都报错）；附加参数须为 JSON 对象。幂等。
     * 级别判断（商业 T2 起）由调用方负责。
     *
     * @return 写入的服务商名称；清单未给出服务商时为 {@code null}（不写库）
     */
    @Nullable
    @Transactional(rollbackFor = Exception.class)
    public String configureAi(Props.Install.AiProvider manifest) {
        if (!manifest.declared()) {
            return null;
        }
        if (StringUtils.isAnyBlank(manifest.getBaseUrl(), manifest.getModel())) {
            throw new IllegalArgumentException("ujcms.install.ai.provider: base-url and model must be given together");
        }
        Config config = configService.getUnique();
        Config.Ai ai = config.getAi();
        Config.Ai.Provider provider = ai.getProviders().stream()
                .filter(it -> MANIFEST_AI_PROVIDER_CODE.equals(it.getCode())).findFirst().orElseGet(() -> {
                    Config.Ai.Provider created = new Config.Ai.Provider();
                    created.setCode(MANIFEST_AI_PROVIDER_CODE);
                    ai.getProviders().add(created);
                    return created;
                });
        String model = manifest.getModel().trim();
        provider.setName(StringUtils.defaultIfBlank(manifest.getName(), model).trim());
        provider.setBaseUrl(manifest.getBaseUrl());
        provider.normalizeBaseUrl();
        provider.setApiKey(StringUtils.trimToEmpty(manifest.getApiKey()));
        provider.setModel(model);
        provider.setExtraParams(StringUtils.trimToEmpty(manifest.getExtraParams()));
        try {
            provider.extraParamsObject();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("ujcms.install.ai.provider.extra-params: " + e.getMessage(), e);
        }
        ai.setDefaultProvider(MANIFEST_AI_PROVIDER_CODE);
        ai.setEnabled(true);
        config.setAi(ai);
        configService.update(config);
        return provider.getName();
    }

    /**
     * 清单机器翻译引擎步骤：把清单给出的百度翻译凭证写入“AI服务商”设置的机器翻译引擎配置（{@link Config.Ai.Mt}），
     * 设为翻译服务商并打开全局开关（机器翻译同样受全局开关控制）。其余引擎的凭证、大模型服务商与其它分工保持原样。
     * APP ID 与密钥须同时给出，只给其一 fail-loud（半套凭证的引擎每次翻译都报“未配置”）。幂等。
     * 级别判断（商业 T2 起）由调用方负责。
     *
     * @return 设为翻译服务商的引擎保留代码（{@link Config.Ai#MT_BAIDU}）；清单未给出凭证时为 {@code null}（不写库）
     */
    @Nullable
    @Transactional(rollbackFor = Exception.class)
    public String configureAiMt(Props.Install.AiMt manifest) {
        Props.Install.AiMtBaidu baidu = manifest.getBaidu();
        if (!baidu.declared()) {
            return null;
        }
        if (StringUtils.isAnyBlank(baidu.getAppId(), baidu.getSecret())) {
            throw new IllegalArgumentException("ujcms.install.ai.mt.baidu: app-id and secret must be given together");
        }
        Config config = configService.getUnique();
        Config.Ai ai = config.getAi();
        Config.Ai.Mt mt = ai.getMt();
        mt.setBaiduAppId(baidu.getAppId().trim());
        mt.setBaiduSecret(baidu.getSecret().trim());
        ai.setMt(mt);
        ai.setTranslationProvider(Config.Ai.MT_BAIDU);
        ai.setEnabled(true);
        config.setAi(ai);
        configService.update(config);
        return Config.Ai.MT_BAIDU;
    }

    /**
     * 主题步骤（§5/§7）：三种来源（{@code bundled} / {@code library} / {@code marketplace}）
     * 都经主题包库（§6）解析，向导与清单共用。幂等再入：目标主题目录已在（开发期 webapp
     * 预展开授权目录，或上次安装已解包）时绝不覆盖——补激活后按 {@code seedScope} 走
     * {@link ThemeSeedOrchestrator} 种子导入（来源三元组幂等，可断点续装）；
     * {@code seedScope=none} 跳过导入得到空站（Q5）。
     *
     * <ul>
     *   <li>{@code bundled}：{@code ref} 缺省取站点当前主题名。目录不存在（裸 JAR，P3 D6）时
     *       回退库内同名包；库内也无包才跳过并报告，不作为安装错误（P1 语义保持）。</li>
     *   <li>{@code library}：{@code ref} 为 {@code 名称[@版本]}，未指定版本取最新可用；
     *       包缺失 fail-loud。</li>
     *   <li>{@code marketplace}：{@code ref} 为 {@code slug[@版本]}，sha256 校验写穿包库，
     *       库命中零重传（{@link MarketplaceInstallService}）。</li>
     * </ul>
     *
     * @param siteId    目标站点（向导恒为站点 1；清单含 T2 追加站点）
     * @param source    主题来源，空值视同 {@code bundled}
     * @param ref       来源引用（见上），可空性随来源
     * @param seedScope {@code all} / {@code none}
     * @return 步骤报告
     */
    public ThemeStepResult installTheme(Long siteId, @Nullable String source, @Nullable String ref,
                                        String seedScope) {
        Site site = siteService.select(siteId);
        if (site == null) {
            throw new IllegalStateException("Site not found: " + siteId);
        }
        boolean seedAll = !SEED_SCOPE_NONE.equals(seedScope);
        ThemeInstallService.SeedScope scope = seedAll
                ? ThemeInstallService.SeedScope.ALL : ThemeInstallService.SeedScope.NONE;
        String effectiveSource = StringUtils.defaultIfBlank(source, Props.Install.Theme.SOURCE_BUNDLED);
        return switch (effectiveSource) {
            case Props.Install.Theme.SOURCE_MARKETPLACE -> {
                ThemeRef themeRef = ThemeRef.parse(ref, effectiveSource);
                if (themeInstallService.themeDir(siteId, themeRef.name()).exists()) {
                    yield seedExisting(site, themeRef.name(), seedAll);
                }
                ThemeInstallReport report = marketplaceInstallService.install(themeRef.name(), themeRef.version(),
                        siteId, themeRef.name(), activateTarget(site, themeRef.name()), scope);
                yield new ThemeStepResult(themeRef.name(), false, "installed from marketplace " + themeRef
                        + seedSuffix(report), report.getSeed());
            }
            case Props.Install.Theme.SOURCE_LIBRARY -> {
                ThemeRef themeRef = ThemeRef.parse(ref, effectiveSource);
                if (themeInstallService.themeDir(siteId, themeRef.name()).exists()) {
                    yield seedExisting(site, themeRef.name(), seedAll);
                }
                ThemePackage pkg = themeRef.version() != null
                        ? themePackageService.findByNameAndVersion(themeRef.name(), themeRef.version())
                        : themePackageService.findUsableByName(themeRef.name()).orElse(null);
                if (pkg == null || !themePackageService.packageFileExists(pkg)) {
                    throw new ThemeInstallException("theme.package.error.notFound",
                            "Library package not found: " + themeRef
                                    + ". Upload it to the package library (or mount it via "
                                    + "ujcms.install.library-dir) first.");
                }
                yield installFromPackage(site, pkg, themeRef.name(), scope);
            }
            case Props.Install.Theme.SOURCE_BUNDLED -> {
                String themeName = StringUtils.defaultIfBlank(ref, site.getTheme());
                File themeDir = themeInstallService.themeDir(siteId, themeName);
                if (themeDir.exists()) {
                    // 开发期授权目录 / 已解包实例：绝不覆盖，只补激活 + 种子
                    yield seedExisting(site, themeName, seedAll);
                }
                // P3 D6 裸 JAR 回退：从库内包安装（解包 + 可选种子）
                Optional<ThemePackage> pkg = themePackageService.findUsableByName(themeName);
                if (pkg.isEmpty()) {
                    logger.warn("Install theme skipped: theme dir not found and no library package: {}", themeDir);
                    yield new ThemeStepResult(themeName, true,
                            "theme dir not found and no library package: " + themeDir, null);
                }
                yield installFromPackage(site, pkg.get(), themeName, scope);
            }
            default -> throw new IllegalStateException("Unknown theme source: " + source
                    + " (expected bundled|library|marketplace)");
        };
    }

    /**
     * 从库内包安装到站点（解包 + 构成门槛 + 可选种子；目标名与站点当前主题不同则激活）。
     */
    private ThemeStepResult installFromPackage(Site site, ThemePackage pkg, String themeName,
                                               ThemeInstallService.SeedScope scope) {
        ActivateTarget activate = activateTarget(site, themeName);
        ThemeInstallReport report = themePackageService.withPackageFile(pkg, path ->
                themeInstallService.install(path, site.getId(), themeName, activate, scope));
        logger.info("Install theme from library package: site={} theme={} package={}@{} seed={}",
                site.getId(), themeName, pkg.getName(), pkg.getVersion(), report.getSeed());
        return new ThemeStepResult(themeName, false, "installed from library package "
                + pkg.getName() + "@" + pkg.getVersion() + seedSuffix(report), report.getSeed());
    }

    /**
     * 安装清单装主题的激活口径：目标名与站点当前桌面主题不同则桌面+手机一并激活（首装站点
     * 两槽位同主题；手机主题的独立选择是装后的控制台动作），相同则只安装不激活。
     */
    @Nullable
    private static ActivateTarget activateTarget(Site site, String themeName) {
        return themeName.equals(site.getTheme()) ? null : ActivateTarget.BOTH;
    }

    /**
     * 幂等再入路径：主题目录已在。未激活则补激活（安装崩溃在解包与激活之间的续装窗口），
     * 随后按种子范围导入（来源三元组幂等）。
     */
    private ThemeStepResult seedExisting(Site site, String themeName, boolean seedAll) {
        if (!themeName.equals(site.getTheme())) {
            themeInstallService.activate(site.getId(), themeName, ActivateTarget.BOTH);
        }
        if (!seedAll) {
            return new ThemeStepResult(themeName, true, "seed skipped (empty site)", null);
        }
        File themeDir = themeInstallService.themeDir(site.getId(), themeName);
        ThemeSeedOrchestrator.SeedRunResult result = seedOrchestrator.importSeed(site.getId(), themeName,
                themeDir, themeInstallService.templateStorageUrl(), INIT_USER_ID, true);
        logger.info("Install theme seed imported: site={} theme={} {}", site.getId(), themeName, result);
        return new ThemeStepResult(themeName, false, "seed imported", result);
    }

    /** 报告短语只带有无种子；计数明细已由上方 info 日志与结构化 {@code seed} 字段承载 */
    private static String seedSuffix(ThemeInstallReport report) {
        return report.getSeed() != null ? " (seed imported)" : " (seed skipped)";
    }

    /**
     * 清单站点幂等创建（§7 / D11 / D44）：首条之外的 {@code sites} 条目经 T2 建站复制路径
     * （{@code copyFromId=1}、空 {@code copyData}——模板/内容由随后的主题步骤提供；语言变体
     * 同样不复制栏目，hreflang/切换器的对等性来自变体播种与主站点<b>同一主题</b>的种子，
     * 栏目别名天然对齐，D46）。幂等键为站点名：同名站点已在即视为已建，续装<b>收敛</b>
     * 条目声明的 lang/langOf/subDir（D45——引擎仅在状态 ≠ INSTALLED 时运行，不会与运营期
     * 编辑冲突）。{@code langOf} 按站点名解析（站点 1 或清单更早条目；前向引用 fail-loud）
     * 并归一到语言组主站点；组完整性经端口内 {@code validateAddress → validateLangGroup}
     * 校验，失败即该站点条目的失败报告行（D33 gather-all）。T2 端口缺席（社区版）fail-loud，
     * 绝不静默降级为单站。
     *
     * @return 站点 ID、是否为续装命中、语言变体链接描述
     */
    public SiteCreateResult ensureManifestSite(Props.Install.SiteEntry entry) {
        String name = entry.getName();
        if (StringUtils.isBlank(name)) {
            throw new IllegalStateException(
                    "Manifest site entries beyond the first require a name (idempotency key)");
        }
        String lang = StringUtils.trimToNull(entry.getLang());
        String langOf = StringUtils.trimToNull(entry.getLangOf());
        String subDir = StringUtils.trimToNull(entry.getSubDir());
        if ((lang == null) != (langOf == null)) {
            throw new IllegalStateException("Manifest site '" + name + "' must set lang and langOf together: "
                    + "a language variant needs both; a plain site sets neither (lang=" + lang
                    + ", langOf=" + langOf + ")");
        }
        validateLangFormat(name, lang);
        validateSubDirFormat(name, subDir);
        Site master = langOf != null ? resolveLangMaster(name, langOf) : null;
        String linkage = master != null ? "variant of '" + master.getName() + "' [" + lang + "]" : null;

        Site existing = findSiteByName(name);
        if (existing != null) {
            boolean converged = convergeManifestSite(existing, entry, lang, master, subDir);
            return new SiteCreateResult(existing.getId(), true,
                    linkage != null && converged ? linkage + " (converged)" : linkage);
        }
        InstallSitePort port = sitePort.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException("Manifest sites beyond the first require the commercial T2 "
                    + "edition (multi-site is a T2 feature, D11): site '" + name + "' not created");
        }
        Site site = new Site();
        site.setName(name);
        site.setOrgId(1L);
        site.setCopyFromId(INIT_SITE_ID);
        site.setCopyData(new ArrayList<>());
        // 变体缺省继承主站点协议/域名——推荐拓扑（同域名子目录 example.com/en/）零配置即得
        String inheritedProtocol = master != null ? master.getProtocol() : "http";
        site.setProtocol(StringUtils.defaultIfBlank(entry.getProtocol(), inheritedProtocol));
        if (StringUtils.isNotBlank(entry.getDomain())) {
            site.setDomain(entry.getDomain());
        } else if (master != null) {
            site.setDomain(master.getDomain());
        }
        if (subDir != null) {
            site.setSubDir(subDir);
        }
        if (master != null) {
            site.setLang(lang);
            site.setLangParentId(master.getId());
        }
        Long id = port.create(site);
        logger.info("Manifest site created via T2 copy path: id={} name={}{}", id, name,
                linkage != null ? " (" + linkage + ")" : "");
        return new SiteCreateResult(id, false, linkage);
    }

    /**
     * 语言组主站点解析（D44）：{@code langOf} 为站点名引用，指向站点 1 或清单更早条目
     * （按清单顺序处理，前向引用在其所在启动即 fail-loud）；引用到变体时归一到其组主站点
     * （组仅一层，清单永远造不出链）。
     */
    private Site resolveLangMaster(String siteName, String langOf) {
        Site named = findSiteByName(langOf);
        if (named == null) {
            throw new IllegalStateException("Manifest site '" + siteName + "' langOf '" + langOf
                    + "' does not resolve to an existing site — langOf must reference site 1 or an earlier "
                    + "manifest entry (entries are processed in manifest order; forward references fail)");
        }
        Long masterId = named.getLangGroupMasterId();
        if (!masterId.equals(named.getId())) {
            return Optional.ofNullable(siteService.select(masterId)).orElseThrow(() ->
                    new IllegalStateException("Lang group master not found: " + masterId));
        }
        return named;
    }

    /**
     * 续装收敛（D45）：同名站点已在时，把条目声明的 lang/langOf/subDir 收敛到既有行——
     * 上次启动因组校验失败留下的未链接站点，修正清单后重启即补齐链接。变体条目另收敛
     * 协议/域名到与全新首装同一的落点（条目值，缺省继承主站点，D44）——收敛终态不随启动
     * 历史漂移（此前以普通条目建成的站点域名为空，链入语言组后 hreflang 绝对 URL 会残缺）。
     * 仅收敛条目声明（或变体有继承缺省）的字段；无变化零写。地址与组完整性经
     * {@code validateAddress} 全量复验。
     *
     * @return 是否发生写入
     */
    private boolean convergeManifestSite(Site site, Props.Install.SiteEntry entry, @Nullable String lang,
                                         @Nullable Site master, @Nullable String subDir) {
        boolean changed = false;
        if (lang != null && !lang.equals(site.getLang())) {
            site.setLang(lang);
            changed = true;
        }
        if (master != null && !master.getId().equals(site.getLangParentId())) {
            site.setLangParentId(master.getId());
            changed = true;
        }
        if (subDir != null && !subDir.equals(site.getSubDir())) {
            site.setSubDir(subDir);
            changed = true;
        }
        if (master != null) {
            String protocol = StringUtils.defaultIfBlank(entry.getProtocol(), master.getProtocol());
            String domain = StringUtils.defaultIfBlank(entry.getDomain(), master.getDomain());
            if (!protocol.equals(site.getProtocol())) {
                site.setProtocol(protocol);
                changed = true;
            }
            if (!domain.equals(site.getDomain())) {
                site.setDomain(domain);
                changed = true;
            }
        }
        if (!changed) {
            return false;
        }
        siteService.validateAddress(site, isMultiDomain());
        siteService.update(site);
        logger.info("Manifest site converged: id={} name={} lang={} langParentId={} subDir={} protocol={} domain={}",
                site.getId(), site.getName(), site.getLang(), site.getLangParentId(), site.getSubDir(),
                site.getProtocol(), site.getDomain());
        return true;
    }

    /**
     * 语言变体的种子译文建链（§7 / D50）：变体站点与语言组内其余站点之间，来源键相同的主题
     * 种子文章链入同一翻译组（{@link ArticleI18nService#linkSeedPeers}）——前台语言切换器与
     * hreflang 自首启起即按文章互通，无需装后逐篇关联。须在全部站点的主题步骤之后调用
     * （种子落库在先）。幂等：已同组的配对只计数；续装只补缺失部分。
     *
     * @param entry 带 {@code langOf} 的清单站点条目
     * @return 建链统计（对组内其余各站点求和）；站点不存在（其建站步骤已失败）返回 {@code null}
     */
    public ArticleI18nService.@Nullable SeedLinkResult linkLangVariantArticles(Props.Install.SiteEntry entry) {
        Site site = StringUtils.isBlank(entry.getName()) ? null : findSiteByName(entry.getName());
        if (site == null) {
            return null;
        }
        int linked = 0;
        int already = 0;
        int conflicts = 0;
        int ambiguous = 0;
        for (Site member : siteService.listByLangGroup(site)) {
            if (member.getId().equals(site.getId())) {
                continue;
            }
            ArticleI18nService.SeedLinkResult result = articleI18nService.linkSeedPeers(site, member);
            linked += result.linked();
            already += result.already();
            conflicts += result.conflicts();
            ambiguous += result.ambiguous();
        }
        logger.info("Manifest lang variant seed links: site={} name={} linked={} already={} conflicts={} ambiguous={}",
                site.getId(), site.getName(), linked, already, conflicts, ambiguous);
        return new ArticleI18nService.SeedLinkResult(linked, already, conflicts, ambiguous);
    }

    @Nullable
    private Site findSiteByName(String name) {
        SiteArgs args = SiteArgs.of();
        args.getQueryMap().put("EQ_name", name);
        List<Site> list = siteService.selectList(args);
        return list.isEmpty() ? null : list.get(0);
    }

    private boolean isMultiDomain() {
        return Boolean.TRUE.equals(configService.getUnique().getMultiDomain());
    }

    private static final Pattern LANG_FORMAT = Pattern.compile(Site.LANG_PATTERN);
    private static final Pattern SUB_DIR_FORMAT = Pattern.compile(Site.SUB_DIR_PATTERN, Pattern.CASE_INSENSITIVE);

    /**
     * 清单路径不经 Bean Validation，格式复用 {@code Site} 的 @Pattern 正则手工复验（D44）。
     */
    private static void validateLangFormat(String siteName, @Nullable String lang) {
        if (lang != null && !LANG_FORMAT.matcher(lang).matches()) {
            throw new IllegalStateException("Manifest site '" + siteName + "' lang '" + lang
                    + "' is not a valid BCP-47 tag (expected e.g. zh-CN, en)");
        }
    }

    private static void validateSubDirFormat(String siteName, @Nullable String subDir) {
        if (subDir != null && !SUB_DIR_FORMAT.matcher(subDir).matches()) {
            throw new IllegalStateException("Manifest site '" + siteName + "' subDir '" + subDir
                    + "' is invalid (word characters only; reserved: uploads|themes|WEB-INF|cp|api|frontend|m)");
        }
    }

    /**
     * 清单用户幂等创建（P5 §8）：幂等键为用户名——同名用户已在即跳过，续装绝不覆盖既有行。
     * {@code readOnlyDemo} 条目以固定 ID {@code Constants.DEMO_USER_ID}（10）落库（C7：
     * {@code ujcms.demo=true} 时 {@code BackendInterceptor} 对该 ID 强制仅 GET），该 ID
     * 已被他名占用时 fail-loud；其余条目经 {@code userService} 常规创建（雪花 ID）。
     * {@code roles} 按角色名在站点 1 作用域解析（引导角色即「管理角色」），未知名称
     * fail-loud。缺省：组织 1、用户组 8（管理组）、等级 0、普通用户、启用、无强制改密；
     * 口令经 {@code PasswordEncoder} 摘要落库，缺失口令 fail-loud（无口令即无法登录，
     * 静默创建只会造出僵尸账号）。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserStepResult ensureUser(Props.Install.UserEntry entry) {
        String username = entry.getUsername();
        if (StringUtils.isBlank(username)) {
            throw new IllegalStateException("Manifest user entries require a username (idempotency key)");
        }
        User existing = userService.selectByUsername(username);
        if (existing != null) {
            return new UserStepResult(existing.getId(), true);
        }
        if (StringUtils.isBlank(entry.getPassword())) {
            throw new IllegalStateException("Manifest user '" + username + "' requires a password");
        }
        List<Role> roles = resolveRoles(entry.getRoles(), username);
        short rank = entry.getRank() != null ? entry.getRank() : 0;
        for (Role role : roles) {
            if (role.getRank() < rank) {
                throw new IllegalStateException("Manifest user '" + username + "' rank(" + rank
                        + ") below role '" + role.getName() + "' rank(" + role.getRank() + ")");
            }
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(entry.getPassword()));
        user.setOrgId(entry.getOrgId() != null ? entry.getOrgId() : 1L);
        user.setGroupId(entry.getGroupId() != null ? entry.getGroupId() : 8L);
        user.setRank(rank);
        user.setType(User.TYPE_NORMAL);
        user.setStatus(User.STATUS_ENABLED);
        if (entry.isReadOnlyDemo()) {
            long demoId = Constants.DEMO_USER_ID;
            User occupant = userService.select(demoId);
            if (occupant != null) {
                throw new IllegalStateException("Demo user id " + demoId + " already taken by '"
                        + occupant.getUsername() + "' — cannot create read-only demo user '" + username + "'");
            }
            // 固定 ID 落库走 Mapper（引导骨架同款）：UserService.insert 恒铸雪花 ID
            user.setId(demoId);
            UserExt ext = new UserExt();
            ext.setId(demoId);
            userMapper.insert(user);
            userExtMapper.insert(ext);
        } else {
            userService.insert(user, new UserExt(), List.of());
        }
        for (Role role : roles) {
            UserRole userRole = new UserRole(user.getId(), role.getId());
            userRole.setOrder(9999);
            userRoleMapper.insert(userRole);
        }
        logger.info("Manifest user created: id={} username={} readOnlyDemo={} roles={}",
                user.getId(), username, entry.isReadOnlyDemo(), entry.getRoles());
        return new UserStepResult(user.getId(), false);
    }

    private List<Role> resolveRoles(List<String> roleNames, String username) {
        if (roleNames.isEmpty()) {
            return List.of();
        }
        List<Role> siteRoles = roleService.selectList(RoleArgs.of().siteId(INIT_SITE_ID));
        List<Role> resolved = new ArrayList<>();
        for (String name : roleNames) {
            Role role = siteRoles.stream().filter(r -> name.equals(r.getName())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Manifest user '" + username
                            + "' references unknown role '" + name + "' (available: "
                            + siteRoles.stream().map(Role::getName).toList() + ")"));
            resolved.add(role);
        }
        return resolved;
    }

    /**
     * 清单用户创建结果
     *
     * @param userId  用户 ID
     * @param existed 同名用户已在（幂等续装命中）
     */
    public record UserStepResult(Long userId, boolean existed) {
    }

    /**
     * 主题来源引用：{@code 名称[@版本]}（marketplace 的名称即 slug）。
     */
    public record ThemeRef(String name, @Nullable String version) {
        static ThemeRef parse(@Nullable String ref, String source) {
            if (StringUtils.isBlank(ref)) {
                throw new IllegalStateException("Theme source '" + source
                        + "' requires a ref (name[@version])");
            }
            int at = ref.lastIndexOf('@');
            if (at > 0 && at < ref.length() - 1) {
                return new ThemeRef(ref.substring(0, at).trim(), ref.substring(at + 1).trim());
            }
            return new ThemeRef(ref.trim(), null);
        }

        @Override
        public String toString() {
            return version != null ? name + "@" + version : name;
        }
    }

    /**
     * 清单站点创建结果
     *
     * @param siteId  站点 ID
     * @param existed 同名站点已在（幂等续装命中）
     * @param linkage 语言变体链接描述（报告行用，如 {@code variant of 'X' [en]}；续装收敛时
     *                带 {@code (converged)} 后缀）。非变体条目为 {@code null}
     */
    public record SiteCreateResult(Long siteId, boolean existed, @Nullable String linkage) {
    }

    /**
     * 完成安装（§5 / D13）：守卫翻转 INSTALLED（单行 UPDATE，唯一赢家），随后由赢家运行
     * {@link FirstInitSeeder}（重锚定：从"首装启动后"改为"安装完成时"）。先翻转后跑
     * seeder（D15）——seeder 插行非幂等（P2 起仅余示例留言，演示投票/调查已迁入主题种子），
     * 翻转即认领保证续装/并发都不会重复播种；seeder 失败一如既往只记日志不阻断
     * （控制台可手动补）。
     *
     * @return 完成报告（含 C5 模板存储一致性检查结果）
     */
    public CompleteResult complete() {
        String warning = checkTemplateStorage();
        int flipped = configMapper.flipInstalled();
        installedMemo = true;
        tokenFile.delete();
        if (flipped == 0) {
            return new CompleteResult(true, true, List.of(), warning);
        }
        List<String> seeders = new ArrayList<>();
        firstInitSeeders.orderedStream().forEach(seeder -> {
            String name = seeder.getClass().getSimpleName();
            try {
                seeder.seed(INIT_SITE_ID, INIT_USER_ID);
                seeders.add(name);
                logger.info("Install-completion seeder done: {}", name);
            } catch (RuntimeException e) {
                seeders.add(name + " (failed)");
                logger.error("Install-completion seeder failed (install continues): {}", name, e);
            }
        });
        if (warning != null) {
            logger.warn("Install template-storage check: {}", warning);
        }
        logger.info("System installed. Install endpoints closed.");
        return new CompleteResult(true, false, seeders, warning);
    }

    /**
     * C5 检查：FreeMarker 模板加载路径（{@code spring.freemarker.template-loader-path}）与
     * {@code Config.templateStorage.path} 是两处必须一致的配置，此前无任何校验。归一化尾部
     * 斜杠后比对；模板存储非本地类型或加载路径含 {@code file:} 前缀等无法静态比对的情形
     * 不报告（避免误报）。
     *
     * @return 不一致描述；一致或无法比对时为 {@code null}
     */
    @Nullable
    public String checkTemplateStorage() {
        String storagePath = configService.getUnique().getTemplateStorage().getPath();
        String[] loaderPaths = freeMarkerProperties.getTemplateLoaderPath();
        String normalizedStorage = stripTrailingSlash(storagePath);
        boolean matched = Arrays.stream(loaderPaths)
                .map(InstallService::stripTrailingSlash)
                .anyMatch(p -> p.equals(normalizedStorage) || p.endsWith(":" + normalizedStorage));
        if (matched) {
            return null;
        }
        return "spring.freemarker.template-loader-path " + Arrays.toString(loaderPaths)
                + " does not contain Config.templateStorage.path '" + storagePath
                + "' — templates will not resolve; align the two settings";
    }

    private static String stripTrailingSlash(String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    /**
     * 主题种子步骤报告
     *
     * @param theme   目标主题名
     * @param skipped 本步骤被跳过（空站点 / 主题目录与库内包均不在场）
     * @param message 简短英文短语（日志与自动安装报告用）
     * @param seed    种子导入结构化结果（向导据此渲染可读摘要与失败明细）；未导入种子时为 null
     */
    public record ThemeStepResult(String theme, boolean skipped, String message,
                                  @Nullable SeedRunResult seed) {
    }

    /**
     * 完成步骤报告
     *
     * @param installed        恒为 true（完成后）
     * @param alreadyInstalled 本次调用前已由他方完成（续装/并发）
     * @param seeders          本次运行的安装完成 seeder（含失败标注；他方完成时为空）
     * @param warning          C5 检查不一致描述，可为 null
     */
    public record CompleteResult(boolean installed, boolean alreadyInstalled, List<String> seeders,
                                 @Nullable String warning) {
    }
}
