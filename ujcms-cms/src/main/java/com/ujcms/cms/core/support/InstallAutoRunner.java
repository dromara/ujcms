package com.ujcms.cms.core.support;

import com.ujcms.cms.core.service.ArticleI18nService;
import com.ujcms.cms.core.service.InstallDemoPort;
import com.ujcms.cms.core.service.InstallService;
import com.ujcms.cms.core.service.InstallService.InstallState;
import com.ujcms.cms.core.service.InstallService.SiteCreateResult;
import com.ujcms.cms.core.service.InstallService.ThemeStepResult;
import com.ujcms.cms.core.service.InstallService.UserStepResult;
import com.ujcms.cms.core.service.ThemePackageService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;

import java.nio.file.Path;
import java.util.List;

/**
 * 无人值守自动安装引擎（install-flow §7）：{@code ujcms.install.auto=true} 时，启动末位在
 * 安装状态为 {@code CORE_READY} 的库上进程内跑完 {@code ujcms.install.*} 清单——包库目录
 * 摄取 → 站点 1 配置（首条 {@code sites}，D4）→ 管理员凭据 → 追加用户（{@code users:}，
 * 含固定 ID 10 的演示只读账号，C7）→ 注册开关、第三方登录凭据、AI 服务商与机器翻译引擎（{@code register:} /
 * {@code oauth:} / {@code ai:}，后两者商业 T2 缺席时跳过并落报告行）→ 站点 1 主题
 * （bundled|library|marketplace，均经包库解析）→ 追加站点（T2 建站复制路径，社区版 fail-loud，D11；langOf 语言变体链接，D44）
 * 及其主题 → 语言变体译文建链（来源键相同的种子文章链入同一翻译组，D50）→ 演示层
 * （{@code demo:}，真实 Flowable 工作流 + 审核状态数据；商业 T1 缺席时跳过并落报告行，
 * P5 §8）→ 全步成功才完成翻转。开发 Profile（main/next/feat/demo）与
 * 主题验收环境借此保持"空库启动即得完整演示站"的零交互体验（§4 硬性要求）。
 *
 * <p>逐步执行并记录 {@link InstallReport}（R2 风格可诉原因行）：单步失败不中断其余步骤
 * （每步幂等，多跑多得——社区版多站清单下站点 1 照常落成），但任一失败即不调用
 * {@code complete()}——状态停留 CORE_READY，报告整体落日志，下次启动重试续装。
 *
 * <p>末位运行（{@code LOWEST_PRECEDENCE}）：晚于 {@code CompositionBootScan}
 * （{@code HIGHEST_PRECEDENCE}）的首个构成注册表快照，种子导入时主题声明的 Shape /
 * 布局变体已可校验——与退役的旧 ApplicationRunner 种子阶段同一时序约束。
 *
 * <p>仅首装路径：已安装（INSTALLED）为空操作，绝不在既有库上自愈重跑（首装初始化例外
 * 规则）；进程内调用不需安装令牌（清单即配置层身份，Q4）。任何失败不阻断启动。
 *
 * @author PONY
 */
public class InstallAutoRunner implements ApplicationRunner, Ordered {
    private static final Logger logger = LoggerFactory.getLogger(InstallAutoRunner.class);
    /**
     * 第三方登录所需的商业版级别（features.yaml oauthLogin）
     */
    private static final int OAUTH_TIER = 2;
    /**
     * AI 服务商所需的商业版级别（features.yaml aiProvider）
     */
    private static final int AI_TIER = 2;

    private final InstallService installService;
    private final ThemePackageService themePackageService;
    private final ObjectProvider<InstallDemoPort> demoPort;
    private final Props props;

    public InstallAutoRunner(InstallService installService, ThemePackageService themePackageService,
                             ObjectProvider<InstallDemoPort> demoPort, Props props) {
        this.installService = installService;
        this.themePackageService = themePackageService;
        this.demoPort = demoPort;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        InstallState state = installService.getState();
        if (state == InstallState.INSTALLED) {
            return;
        }
        if (state == InstallState.NOT_INSTALLED) {
            logger.warn("Auto install skipped: system core not ready (bootstrap disabled or failed)");
            return;
        }
        Props.Install manifest = props.getInstall();
        InstallReport report = new InstallReport();

        ingestLibraryDir(manifest, report);
        configureSite1(manifest, report);
        configureAdmin(manifest, report);
        ensureUsers(manifest, report);
        configureRegister(manifest, report);
        configureOauth(manifest, report);
        configureAi(manifest, report);
        configureAiMt(manifest, report);
        installSiteThemes(manifest, report);
        linkLangVariants(manifest, report);
        provisionDemo(manifest, report);

        if (report.hasFailure()) {
            logger.error("Auto install incomplete: state stays CORE_READY, next boot retries "
                    + "(every step is idempotent); or install via console /cp/.\n{}", report.format());
            return;
        }
        try {
            InstallService.CompleteResult result = installService.complete();
            report.ok("complete", "seeders=" + result.seeders() + " alreadyInstalled=" + result.alreadyInstalled()
                    + (result.warning() != null ? " warning=" + result.warning() : ""));
            logger.info("Auto install completed.\n{}", report.format());
        } catch (RuntimeException e) {
            report.failed("complete", String.valueOf(e));
            logger.error("Auto install failed at completion: state stays CORE_READY, next boot retries.\n{}",
                    report.format(), e);
        }
    }

    /**
     * 包库目录摄取（§7）：单个 ZIP 失败只记失败行，不判整步失败——引用该包的主题步骤自会
     * fail-loud；目录不可读则整步失败。
     */
    private void ingestLibraryDir(Props.Install manifest, InstallReport report) {
        String libraryDir = manifest.getLibraryDir();
        if (StringUtils.isBlank(libraryDir)) {
            return;
        }
        try {
            ThemePackageService.IngestResult result = themePackageService.ingestDirectory(Path.of(libraryDir));
            String summary = "registered=" + result.registered() + " skipped=" + result.skipped();
            if (!result.failures().isEmpty() && result.registered() == 0 && result.skipped() == 0) {
                report.failed("libraryDir", summary + " failures=" + result.failures());
            } else if (!result.failures().isEmpty()) {
                report.ok("libraryDir", summary + " failures=" + result.failures());
            } else {
                report.ok("libraryDir", summary);
            }
        } catch (RuntimeException e) {
            logger.error("Auto install step failed: libraryDir", e);
            report.failed("libraryDir", String.valueOf(e));
        }
    }

    private void configureSite1(Props.Install manifest, InstallReport report) {
        Props.Install.SiteEntry first = manifest.firstSite();
        try {
            installService.configureSite1(first);
            report.ok("site1", "name=" + StringUtils.defaultIfBlank(first.getName(), "(keep)")
                    + " domain=" + StringUtils.defaultIfBlank(first.getDomain(), "(keep)")
                    + (StringUtils.isNotBlank(first.getLang()) ? " lang=" + first.getLang() : "")
                    + (StringUtils.isNotBlank(first.getSubDir()) ? " subDir=" + first.getSubDir() : ""));
        } catch (RuntimeException e) {
            logger.error("Auto install step failed: site1", e);
            report.failed("site1", String.valueOf(e));
        }
    }

    private void configureAdmin(Props.Install manifest, InstallReport report) {
        Props.Install.Admin admin = manifest.getAdmin();
        try {
            installService.configureAdmin(admin.getUsername(), admin.getPassword(), admin.isMustChangePassword());
            report.ok("admin", "username=" + StringUtils.defaultIfBlank(admin.getUsername(), "(keep)"));
        } catch (RuntimeException e) {
            logger.error("Auto install step failed: admin", e);
            report.failed("admin", String.valueOf(e));
        }
    }

    /**
     * 清单用户步骤（P5）：逐条幂等创建（幂等键为用户名，同名已在即跳过）；单条失败只判
     * 该条失败，不中断其余条目。{@code readOnlyDemo} 条目以固定 ID 10 落库（C7）。
     */
    private void ensureUsers(Props.Install manifest, InstallReport report) {
        for (Props.Install.UserEntry entry : manifest.getUsers()) {
            String label = "user[" + StringUtils.defaultIfBlank(entry.getUsername(), "?") + "]";
            try {
                UserStepResult result = installService.ensureUser(entry);
                if (result.existed()) {
                    report.skipped(label, "already exists, id=" + result.userId());
                } else {
                    report.ok(label, "created, id=" + result.userId()
                            + (entry.isReadOnlyDemo() ? " (read-only demo)" : ""));
                }
            } catch (RuntimeException e) {
                logger.error("Auto install step failed: {}", label, e);
                report.failed(label, String.valueOf(e));
            }
        }
    }

    /**
     * 注册开关步骤：清单给出 {@code register.enabled} 才写入“注册设置”，缺省不落报告行。
     */
    private void configureRegister(Props.Install manifest, InstallReport report) {
        Boolean enabled = manifest.getRegister().getEnabled();
        try {
            if (installService.configureRegister(enabled)) {
                report.ok("register", "enabled=" + enabled);
            }
        } catch (RuntimeException e) {
            logger.error("Auto install step failed: register", e);
            report.failed("register", String.valueOf(e));
        }
    }

    /**
     * 第三方登录步骤：把清单给出凭据的服务商写入“全局设置 › 第三方登录”并启用。商业 T2 类路径缺席（社区版、基础版）时
     * <b>跳过</b>并落报告行——与演示层同为降级语义：少了第三方登录入口，不该把安装卡在 CORE_READY。
     * 清单未给出任何凭据时不落报告行。
     */
    private void configureOauth(Props.Install manifest, InstallReport report) {
        Props.Install.Oauth oauth = manifest.getOauth();
        boolean declared = !StringUtils.isAllBlank(
                oauth.getWeixin().getClientId(), oauth.getWeixin().getClientSecret(),
                oauth.getQq().getClientId(), oauth.getQq().getClientSecret(),
                oauth.getWeibo().getClientId(), oauth.getWeibo().getClientSecret());
        if (!declared) {
            return;
        }
        if (Commercial.getTier() < OAUTH_TIER) {
            report.skipped("oauth", "commercial T2 classpath absent — third-party login credentials "
                    + "not applied (community / T1 edition)");
            return;
        }
        try {
            report.ok("oauth", "enabled=" + installService.configureOauth(oauth));
        } catch (RuntimeException e) {
            logger.error("Auto install step failed: oauth", e);
            report.failed("oauth", String.valueOf(e));
        }
    }

    /**
     * AI 服务商步骤：把清单给出的服务商写入“全局设置 › AI服务商”、设为默认服务商并打开全局开关。商业 T2 类路径缺席
     * （社区版、基础版）时<b>跳过</b>并落报告行——与第三方登录同为降级语义。清单未给出服务商时不落报告行。
     */
    private void configureAi(Props.Install manifest, InstallReport report) {
        Props.Install.AiProvider provider = manifest.getAi().getProvider();
        if (!provider.declared()) {
            return;
        }
        if (Commercial.getTier() < AI_TIER) {
            report.skipped("ai", "commercial T2 classpath absent — AI provider not applied (community / T1 edition)");
            return;
        }
        try {
            report.ok("ai", "defaultProvider=" + installService.configureAi(provider)
                    + " model=" + provider.getModel().trim());
        } catch (RuntimeException e) {
            logger.error("Auto install step failed: ai", e);
            report.failed("ai", String.valueOf(e));
        }
    }

    /**
     * 机器翻译引擎步骤：把清单给出的百度翻译凭证写入“全局设置 › AI服务商”、设为翻译服务商并打开全局开关。
     * 级别与降级语义同 AI 服务商步骤（商业 T2 缺席时跳过并落报告行）。清单未给出凭证时不落报告行。
     */
    private void configureAiMt(Props.Install manifest, InstallReport report) {
        Props.Install.AiMt mt = manifest.getAi().getMt();
        if (!mt.getBaidu().declared()) {
            return;
        }
        if (Commercial.getTier() < AI_TIER) {
            report.skipped("aiMt", "commercial T2 classpath absent — machine translation engine not applied "
                    + "(community / T1 edition)");
            return;
        }
        try {
            report.ok("aiMt", "translationProvider=" + installService.configureAiMt(mt));
        } catch (RuntimeException e) {
            logger.error("Auto install step failed: aiMt", e);
            report.failed("aiMt", String.valueOf(e));
        }
    }

    /**
     * 演示层步骤（P5 §8 / D9）：驱动真实 Flowable 引擎。商业 T1 类路径缺席（社区版）时
     * <b>跳过</b>并落报告行——与多站点的 fail-loud（D11）相反：多站点缺席即清单未兑现，
     * 演示层缺席只是少了演示内容，不该把安装卡在 CORE_READY（tier-port 降级先例）。
     * 审核状态数据要求工作流已供给（种子导入之后、绑定在先），故两步都排在主题步骤之后。
     */
    private void provisionDemo(Props.Install manifest, InstallReport report) {
        Props.Install.Demo demo = manifest.getDemo();
        if (!demo.isWorkflow() && !demo.isReviewStates()) {
            return;
        }
        InstallDemoPort port = demoPort.getIfAvailable();
        if (port == null) {
            report.skipped("demo", "commercial T1 classpath absent — demo workflow/review-state "
                    + "provisioning skipped (community edition)");
            return;
        }
        if (demo.isWorkflow()) {
            try {
                report.ok("demoWorkflow", port.provisionWorkflow(InstallService.INIT_SITE_ID));
            } catch (RuntimeException e) {
                logger.error("Auto install step failed: demoWorkflow", e);
                report.failed("demoWorkflow", String.valueOf(e));
            }
        }
        if (demo.isReviewStates()) {
            try {
                report.ok("demoReviewStates", port.provisionReviewStates(InstallService.INIT_SITE_ID));
            } catch (RuntimeException e) {
                logger.error("Auto install step failed: demoReviewStates", e);
                report.failed("demoReviewStates", String.valueOf(e));
            }
        }
    }

    /**
     * 站点主题步骤：首条作用于站点 1；此后条目先 T2 幂等建站（失败即跳过其主题步骤），
     * 再装清单主题。
     */
    private void installSiteThemes(Props.Install manifest, InstallReport report) {
        Props.Install.SiteEntry first = manifest.firstSite();
        installThemeStep(InstallService.INIT_SITE_ID, "site1", first.getTheme(), report);

        List<Props.Install.SiteEntry> sites = manifest.getSites();
        for (int i = 1; i < sites.size(); i++) {
            Props.Install.SiteEntry entry = sites.get(i);
            String label = "site[" + StringUtils.defaultIfBlank(entry.getName(), "#" + i) + "]";
            Long siteId;
            try {
                SiteCreateResult created = installService.ensureManifestSite(entry);
                siteId = created.siteId();
                report.ok(label, (created.existed() ? "already exists, id=" : "created, id=") + siteId
                        + (created.linkage() != null ? " — " + created.linkage() : ""));
            } catch (RuntimeException e) {
                logger.error("Auto install step failed: {}", label, e);
                report.failed(label, String.valueOf(e));
                continue;
            }
            installThemeStep(siteId, label, entry.getTheme(), report);
        }
    }

    /**
     * 语言变体译文建链步骤（D50）：每个带 {@code langOf} 的条目，把其种子文章与语言组内其余
     * 站点上来源键相同的种子文章链入同一翻译组。排在全部主题步骤之后（种子落库在先）、演示层
     * 之前。冲突与歧义只进摘要、不判失败（建链是锦上添花，不该把安装卡在 CORE_READY）；
     * 站点不存在（其建站步骤已失败并落过报告行）记为跳过。
     */
    private void linkLangVariants(Props.Install manifest, InstallReport report) {
        List<Props.Install.SiteEntry> sites = manifest.getSites();
        for (int i = 1; i < sites.size(); i++) {
            Props.Install.SiteEntry entry = sites.get(i);
            if (StringUtils.isBlank(entry.getLangOf())) {
                continue;
            }
            String step = "langLinks[" + StringUtils.defaultIfBlank(entry.getName(), "#" + i) + "]";
            try {
                ArticleI18nService.SeedLinkResult result = installService.linkLangVariantArticles(entry);
                if (result == null) {
                    report.skipped(step, "site not found — its site step did not complete");
                    continue;
                }
                report.ok(step, "linked=" + result.linked() + " already=" + result.already()
                        + (result.conflicts() > 0 ? " conflicts=" + result.conflicts() : "")
                        + (result.ambiguous() > 0 ? " ambiguous=" + result.ambiguous() : ""));
            } catch (RuntimeException e) {
                logger.error("Auto install step failed: {}", step, e);
                report.failed(step, String.valueOf(e));
            }
        }
    }

    private void installThemeStep(Long siteId, String siteLabel, Props.Install.Theme theme, InstallReport report) {
        String step = "theme[" + siteLabel + "]";
        try {
            ThemeStepResult result = installService.installTheme(siteId, theme.getSource(), theme.getRef(),
                    theme.getSeed());
            if (result.skipped()) {
                report.skipped(step, result.message());
            } else {
                report.ok(step, result.theme() + ": " + result.message());
            }
        } catch (RuntimeException e) {
            logger.error("Auto install step failed: {}", step, e);
            report.failed(step, String.valueOf(e));
        }
    }

    @Override
    public int getOrder() {
        // 末位运行：确保晚于 CompositionBootScan（HIGHEST_PRECEDENCE）的首个注册表快照
        return Ordered.LOWEST_PRECEDENCE;
    }
}
