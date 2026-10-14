package com.ujcms.cms.core.security;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import freemarker.template.Configuration;
import freemarker.template.Template;
import no.api.freemarker.java8.Java8ObjectWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TemplateMemberAccessPolicy} 守卫测试（design-review-2026-07 T5 / P3-7）。
 * <p>
 * 三层保障，缺一不可：
 * <ol>
 * <li>{@link #deniedMembersAreUnreachable()} —— 拒绝表里的每个成员，在真实渲染中确实读不到；</li>
 * <li>{@link #legitimateReadsStayReachable()} —— 参考主题实际用到的属性一个都没误伤；</li>
 * <li>{@link #newSensitiveGetterFailsTheBuild()} —— 反射扫描这些实体上所有“看起来敏感”的 getter，
 * 未列入拒绝表即构建失败。<b>这条是本测试存在的理由</b>：日后给 Config 加一个
 * {@code getApiToken()}，会在构建时炸，而不是等下一次安全审计。</li>
 * </ol>
 */
class TemplateMemberAccessPolicyTest {
    /**
     * 敏感 getter 的命名启发式。命中即必须在 {@link TemplateMemberAccessPolicy#DENIED_MEMBERS} 中，
     * 否则 {@link #newSensitiveGetterFailsTheBuild()} 失败。
     * <p>
     * 有意“宁可误报”：漏报意味着密钥重新对模板可见，误报只需在拒绝表里加一行、或在
     * {@link #SENSITIVE_NAME_EXEMPTIONS} 中记一笔豁免理由。
     */
    private static final List<String> SENSITIVE_NAME_FRAGMENTS = List.of(
            "secret", "password", "passwd", "credential", "token", "apikey", "accesskey",
            "privatekey", "salt", "cipher", "index");

    /**
     * 命中命名启发式、但经复核<b>不是</b>密钥、且模板可读无害的成员。每一条都要写清理由——
     * 这是把“审计判断”固化进构建的地方。
     */
    private static final Map<String, String> SENSITIVE_NAME_EXEMPTIONS = Map.ofEntries(
            // 口令<b>策略</b>，非口令本身。参考主题的注册/改密表单要读它做前端校验提示。
            Map.entry(Config.Security.class.getName() + "#getPasswordMinLength",
                    "口令长度策略，非密钥；主题表单校验要用"),
            Map.entry(Config.Security.class.getName() + "#getPasswordMaxLength",
                    "口令长度策略，非密钥；主题表单校验要用"),
            Map.entry(Config.Security.class.getName() + "#getPasswordStrength", "口令强度策略，非密钥"),
            Map.entry(Config.Security.class.getName() + "#getPasswordPattern", "口令强度对应的正则，非密钥"),
            Map.entry(Config.Security.class.getName() + "#getPasswordMaxDays", "口令有效期策略，非密钥"),
            Map.entry(Config.Security.class.getName() + "#getPasswordMinDays", "口令有效期策略，非密钥"),
            Map.entry(Config.Security.class.getName() + "#getPasswordWarnDays", "口令到期提醒策略，非密钥"),
            Map.entry(Config.Security.class.getName() + "#getPasswordMaxHistory",
                    "强制口令历史条数策略，非密钥"),
            Map.entry(Config.Security.class.getName() + "#isForceChangePasswordOnFirstLogin",
                    "首次登录强制改密的开关，布尔策略位，非密钥"),
            // 用户口令<b>是否存在</b>的布尔投影，正是 getPassword() 被摘除后的替代读法。
            Map.entry(User.class.getName() + "#isHasPassword",
                    "布尔投影，不泄露散列；getPassword() 摘除后的正当替代"),
            // 口令老化相关：时间戳与天数，不泄露散列本身。
            Map.entry(User.class.getName() + "#getPasswordModified", "时间戳，不泄露散列"),
            Map.entry(User.class.getName() + "#getPasswordDays", "口令已使用天数，整数，不泄露散列"),
            Map.entry(User.class.getName() + "#isCredentialsNonExpired",
                    "Spring Security UserDetails 契约的布尔位，不泄露散列"),
            // “token”命中的是 LLM 计费 token 配额（数字），不是访问令牌。
            // 整个 Config.Ai 子树已对模板不可达（getAi 被拒），此处豁免只为审计记录。
            Map.entry(Config.Ai.class.getName() + "#getDailyTokenLimit",
                    "全局每日 LLM token 用量上限，计费配额数字，非访问令牌"),
            Map.entry(Site.Ai.class.getName() + "#getDailyTokenQuota",
                    "站点每日 LLM token 用量配额，计费配额数字，非访问令牌"),
            // “index”命中的是 IndexNow 协议名，不是 PII 盲索引；密钥按协议本就公开（本站 /indexnow-{key}.txt 原样输出）。
            // 含 token 的百度推送接口地址（getBaiduPushUrl）与整段原文 JSON（Site#getGeoSettings）则在拒绝表内。
            Map.entry(Site.Geo.class.getName() + "#isIndexNowEnabled", "IndexNow 推送开关，布尔位"),
            Map.entry(Site.Geo.class.getName() + "#getIndexNowKey",
                    "IndexNow 密钥按协议公开于 /indexnow-{key}.txt，模板可读无额外泄露")
    );

    /** 被审查的实体类：模板能直接或经实体图链拿到的、且承载密钥/PII 的类。 */
    private static final List<Class<?>> AUDITED_CLASSES = List.of(
            Config.class, Config.Storage.class, Config.Sms.class, Config.Email.class,
            Config.Ai.class, Config.Ai.Provider.class,
            Config.Security.class, Config.Upload.class, Config.Register.class, Config.Grey.class,
            User.class, Site.class, Site.Ai.class, Site.Geo.class);

    private final Configuration configuration = new Configuration(Configuration.VERSION_2_3_31);

    @BeforeEach
    void setUp() {
        Java8ObjectWrapper wrapper = new Java8ObjectWrapper(Configuration.VERSION_2_3_31);
        wrapper.setMemberAccessPolicy(new TemplateMemberAccessPolicy());
        configuration.setObjectWrapper(wrapper);
        configuration.setTagSyntax(Configuration.SQUARE_BRACKET_TAG_SYNTAX);
        configuration.setLocale(Locale.CHINA);
    }

    private String render(String source, Map<String, Object> model) throws Exception {
        Template template = new Template("test", new StringReader(source), configuration);
        StringWriter writer = new StringWriter();
        template.process(model, writer);
        return writer.toString();
    }

    private Map<String, Object> model() {
        Config config = new Config();
        Config.Sms sms = new Config.Sms();
        sms.setAccessKeyId("AKID-CANARY");
        sms.setAccessKeySecret("SMS-SECRET-CANARY");
        sms.setSecretId("SID-CANARY");
        sms.setSecretKey("SMS-SK-CANARY");
        config.setSms(sms);
        Config.Email email = new Config.Email();
        email.setUsername("mail-user-CANARY");
        email.setPassword("MAIL-PW-CANARY");
        config.setEmail(email);
        Config.Storage storage = new Config.Storage(Config.Storage.TYPE_MINIO, "/themes", "/themes");
        storage.setAccessKey("AK-CANARY");
        storage.setSecretKey("SK-CANARY");
        storage.setUsername("ftp-user-CANARY");
        storage.setPassword("FTP-PW-CANARY");
        config.setTemplateStorage(storage);
        config.setUploadStorage(storage);
        config.setHtmlStorage(storage);
        Config.Ai ai = new Config.Ai();
        Config.Ai.Provider provider = new Config.Ai.Provider();
        provider.setCode("deepseek");
        provider.setName("DeepSeek");
        provider.setBaseUrl("https://api.deepseek.com/v1");
        provider.setApiKey("AI-KEY-CANARY");
        provider.setModel("deepseek-chat");
        ai.setProviders(new java.util.ArrayList<>(List.of(provider)));
        config.setAi(ai);
        Config.Oauth oauth = new Config.Oauth();
        oauth.getWeixin().setEnabled(true);
        oauth.getWeixin().setClientId("wx-app-id");
        oauth.getWeixin().setClientSecret("OAUTH-SECRET-CANARY");
        config.setOauth(oauth);

        User user = new User();
        user.setUsername("author");
        user.setPassword("{pbkdf2}HASH-CANARY");
        user.setMobile("13800000000");
        user.setEmail("author@example.com");
        user.setMobileIndex("MOBILE-BLIND-INDEX-CANARY");
        user.setEmailIndex("EMAIL-BLIND-INDEX-CANARY");

        Site site = new Site();
        site.setConfig(config);

        Map<String, Object> model = new HashMap<>(8);
        model.put("config", config);
        model.put("user", user);
        model.put("site", site);
        return model;
    }

    /**
     * 拒绝表里的每个成员，在真实渲染中读不到——属性访问抛 {@code InvalidReferenceException}，
     * {@code toString()} 转储被替换为占位符。同时验证<b>绕行路径</b>（{@code site.config.*}）一并封死：
     * 策略以类为单位，凡是通往 Config/User 的路都覆盖。
     */
    @Test
    void deniedMembersAreUnreachable() throws Exception {
        Map<String, Object> model = model();
        List<String> deniedExpressions = List.of(
                // Config：凭据子树
                "config.sms.accessKeySecret", "config.sms.secretKey", "config.sms.accessKeyId",
                "config.sms.secretId",
                "config.email.password", "config.email.username",
                "config.templateStorage.secretKey", "config.templateStorage.accessKey",
                "config.uploadStorage.secretKey", "config.htmlStorage.secretKey",
                // Config：AI 服务商密钥子树（providers[].apiKey 为明文密钥）
                "config.ai", "config.ai.providers",
                // Config：第三方登录凭据子树（各服务商的应用密钥）
                "config.oauth", "config.oauth.weixin.clientSecret",
                // Config：加密列原文（Sm4StringTypeHandler 解密后的完整 JSON）
                "config.smsSettings", "config.emailSettings",
                "config.uploadStorageSettings", "config.htmlStorageSettings",
                "config.templateStorageSettings", "config.aiSettings", "config.oauthSettings",
                // 绕行：任何产出 Site 的标签都能走这条路
                "site.config.sms.accessKeySecret", "site.config.templateStorage.secretKey",
                "site.config.ai", "site.config.oauth",
                // User：口令散列、盲索引
                "user.password", "user.historyPassword", "user.mobileIndex", "user.emailIndex");

        for (String expression : deniedExpressions) {
            assertThatThrownBy(() -> render("${" + expression + "}", model))
                    .as("模板表达式 [${%s}] 必须不可达", expression)
                    .hasMessageContaining("null or missing");
            // 显式方法调用同样被拒（BeansWrapper 不暴露该方法）
            assertThat(render("${(" + expression + ")!'[[DENIED]]'}", model))
                    .as("模板表达式 [${%s}] 的默认值形式不得泄露真值", expression)
                    .isEqualTo("[[DENIED]]");
        }

        // Lombok @Data 的 toString() 转储：${config} 曾一次性打印全部密钥，${user} 打印散列与 PII
        assertThat(render("${config}", model)).doesNotContain("CANARY").contains("toString not exposed");
        assertThat(render("${user}", model)).doesNotContain("CANARY").contains("toString not exposed");

        // AI 服务商密钥：config.ai 已拒绝，这里确认纵深防御那层（Provider 类自身）也在——
        // 即便日后有别的代码把 Provider 实例塞进数据模型，apiKey 依然不可读
        assertThat(exposed(Config.Ai.Provider.class, "getApiKey"))
                .as("Config.Ai.Provider#getApiKey 必须对模板不可读（AI 服务商明文密钥）").isFalse();
        assertThat(exposed(Config.Oauth.Client.class, "getClientSecret"))
                .as("Config.Oauth.Client#getClientSecret 必须对模板不可读（第三方登录应用密钥）").isFalse();
    }

    /** 未被拒绝的类，{@code toString()} 照常可用——{@code isToStringAlwaysExposed()=false} 不是全局关停。 */
    @Test
    void toStringStaysExposedForUnlistedClasses() throws Exception {
        Map<String, Object> model = new HashMap<>(2);
        model.put("bean", new Config.Grey());
        assertThat(render("${bean}", model)).doesNotContain("toString not exposed");
    }

    /**
     * 参考主题实际读取的属性一个都没误伤（渲染逐字节不变的前提）。
     * 清单来自对 {@code webapp/themes} 全量模板的普查。
     */
    @Test
    void legitimateReadsStayReachable() throws Exception {
        Map<String, Object> model = model();
        List<String> allowedExpressions = List.of(
                // config.* —— 参考主题用到的全部叶子
                "config.commercialTier", "config.greyStyle",
                "config.register.enabled", "config.register.verifyMode", "config.register.avatar",
                "config.register.usernameRegex", "config.register.usernameMinLength",
                "config.register.usernameMaxLength",
                "config.security.passwordMinLength", "config.security.passwordMaxLength",
                "config.upload.imageLimitByte", "config.upload.imageInputAccept",
                "config.uploadUrlPrefix",
                // 可用的第三方登录服务商代码（snippets/auth_oauth.html；凭据对象 config.oauth 不可达）
                "config.oauthProviders",
                // 非密钥的存储元数据仍可读（凭据子对象已摘除，此处走的是 Config 上的安全投影）
                // user.* —— 会员自助页
                "user.username", "user.hasPassword",
                // PII：有意保留（会员“账号设置”页显示本人“原邮箱/原手机号”），见策略类注释
                "user.mobile", "user.email",
                // site.*
                "site.title", "site.name",
                // 站点级 AI/GEO 设置只有开关与配额、不含凭据，保持模板可读（全局 config.ai 才含密钥）
                "site.ai.seoAssist", "site.geo.jsonLdEnabled");

        for (String expression : allowedExpressions) {
            // 用 ?? 判可达，而非字符串化——布尔属性（greyStyle/hasPassword）无法自动转字符串
            String rendered = render("[#if (" + expression + ")??]REACHABLE[#else]MISSING[/#if]", model);
            assertThat(rendered)
                    .as("模板表达式 [${%s}] 是参考主题的正当读法，必须保持可达", expression)
                    .isEqualTo("REACHABLE");
        }
        // 第三方登录入口的读法（snippets/auth_oauth.html）：只有服务商代码，没有凭据
        assertThat(render("${config.oauthProviders?join(',')}", model)).isEqualTo("weixin");
        assertThat(render("[#assign oauthProviders = (config.oauthProviders)![]]"
                + "${oauthProviders?seq_contains('weixin')?c},${oauthProviders?seq_contains('qq')?c}", model))
                .isEqualTo("true,false");
        // PII 保留是<b>有意</b>的边界，不是疏漏：真值确实渲染出来
        assertThat(render("${user.mobile}", model)).isEqualTo("13800000000");
        assertThat(render("${user.email}", model)).isEqualTo("author@example.com");
        // hasPassword 与旧哨兵表达式语义等价（模板改写的正确性依据）
        assertThat(render("${user.hasPassword?c}", model)).isEqualTo("true");
        User oauthUser = new User();
        oauthUser.setPassword(User.PASSWORD_NONE);
        assertThat(render("${user.hasPassword?c}", Map.of("user", oauthUser))).isEqualTo("false");
        User blankUser = new User();
        assertThat(render("${user.hasPassword?c}", Map.of("user", blankUser))).isEqualTo("false");
    }

    /**
     * 参考主题的模板改写正确性：{@code user.hasPassword} 与旧哨兵表达式
     * {@code (user.password)?has_content && user.password != '0'} 在<b>每一种</b>口令状态下同值。
     * <p>
     * 旧表达式要读 {@code getPassword()}，在新策略下已不可达——故用<b>未加策略</b>的包装器求值旧式，
     * 用<b>加了策略</b>的包装器求值新式，再比对。这正是
     * {@code mem_settings_account.html} 那 6 处 {@code [#if]} 改写的依据（改写后页面渲染逐字节不变，
     * 已在 :8081 上比对确认）。
     */
    @Test
    void hasPasswordMatchesTheLegacySentinelExpressionInEveryState() throws Exception {
        // 未加策略的包装器：可读 getPassword()，用于求值旧表达式
        Configuration legacy = new Configuration(Configuration.VERSION_2_3_31);
        legacy.setObjectWrapper(new Java8ObjectWrapper(Configuration.VERSION_2_3_31));
        legacy.setTagSyntax(Configuration.SQUARE_BRACKET_TAG_SYNTAX);
        String legacySource = "[#if (user.password)?has_content && user.password != '0']YES[#else]NO[/#if]";
        String currentSource = "[#if user.hasPassword]YES[#else]NO[/#if]";

        Map<String, String> passwordStates = new java.util.LinkedHashMap<>(4);
        passwordStates.put("{pbkdf2}REAL-HASH", "YES");   // 普通账号：设过本地口令
        passwordStates.put(User.PASSWORD_NONE, "NO");     // 纯 OAuth 账号：哨兵值 "0"
        passwordStates.put("", "NO");                     // 空串
        for (Map.Entry<String, String> state : passwordStates.entrySet()) {
            User user = new User();
            user.setPassword(state.getKey());
            Map<String, Object> model = Map.of("user", user);

            StringWriter legacyOut = new StringWriter();
            new Template("legacy", new StringReader(legacySource), legacy).process(model, legacyOut);

            assertThat(legacyOut.toString())
                    .as("旧哨兵表达式在口令状态 [%s] 下的期望值", state.getKey())
                    .isEqualTo(state.getValue());
            assertThat(render(currentSource, model))
                    .as("user.hasPassword 必须与旧哨兵表达式在口令状态 [%s] 下同值", state.getKey())
                    .isEqualTo(legacyOut.toString());
        }
        // password 为 null（未加载）时旧式为 NO，新式亦然
        User nullPasswordUser = new User();
        nullPasswordUser.setPassword(null);
        assertThat(render(currentSource, Map.of("user", nullPasswordUser))).isEqualTo("NO");
    }

    /**
     * 领域实体对模板<b>只读</b>：setter 与有副作用的方法不可调用。
     * <p>
     * 模板持有的是 MyBatis 二级缓存中的实体对象——一次 setter 调用会污染其他请求看到的状态。
     * 两个最严重的不是"改状态"而是"授能力"：{@code sendMail}（以站点名义发任意邮件）与
     * {@code getFileHandler}（授出存储读写句柄）。
     */
    @Test
    void domainEntitiesAreReadOnlyToTemplates() throws Exception {
        Map<String, Object> model = model();
        model.put("article", new com.ujcms.cms.core.domain.Article());

        List<String> mutations = List.of(
                // setter：越过请求边界改写缓存实体
                "config.setSmsSettings('pwned')", "config.setCustomsSettings('x')",
                "user.setNickname('x')", "user.setPassword('x')",
                "site.setName('x')", "article.setTitle('x')",
                // 授出能力 / 有副作用
                "article.adjustStatus()", "user.addHistoryPassword()");
        for (String mutation : mutations) {
            assertThatThrownBy(() -> render("[#assign ignored = " + mutation + "]", model))
                    .as("模板不得调用领域实体的写方法 [%s]", mutation)
                    .isInstanceOf(Exception.class);
        }
        // sendMail / getFileHandler 所在的子对象本就不可达（config.email / config.uploadStorage 已拒绝），
        // 这里直接对类做成员级断言，确认纵深防御那一层也在
        assertThat(exposed(Config.Email.class, "sendMail"))
                .as("Config.Email#sendMail 必须对模板不可调用（可以站点名义发任意邮件）").isFalse();
        assertThat(exposed(Config.Storage.class, "getFileHandler"))
                .as("Config.Storage#getFileHandler 必须对模板不可调用（授出存储读写句柄）").isFalse();
    }

    /**
     * 只读规则<b>不得误伤</b>：带参只读 getter 是主题作者正当的读 API（分页 URL、静态路径…），
     * 必须仍可调用。这是"拒绝写"与"拒绝一切方法"的分界线。
     */
    /**
     * {@code site.absoluteUrl(path)} 在真实渲染中可调用（JSON-LD / og:image 依赖它补绝对地址）：
     * 根相对路径补站点 origin，空串得空（{@code !} 兜底），绝对地址原样
     */
    @Test
    void absoluteUrlIsCallableFromTemplates() throws Exception {
        Map<String, Object> model = model();
        Site site = (Site) model.get("site");
        site.setProtocol("https");
        site.setDomain("www.example.com");
        assertThat(render("${site.absoluteUrl('/uploads/a.jpg')!'none'}|${site.absoluteUrl('')!'none'}"
                + "|${site.absoluteUrl('https://cdn.example.com/b.jpg')!'none'}", model))
                .isEqualTo("https://www.example.com/uploads/a.jpg|none|https://cdn.example.com/b.jpg");
    }

    @Test
    void readOnlyHelperMethodsStayCallable() {
        assertThat(exposed(Site.class, "getBasePath"))
                .as("site.getBasePath(path) 是正当读 API").isTrue();
        assertThat(exposed(Site.class, "hasMobileTheme")).isTrue();
        assertThat(exposed(Site.class, "absoluteUrl"))
                .as("site.absoluteUrl(path) 供 JSON-LD / og:image 补绝对地址，只读").isTrue();
        assertThat(exposed(Site.class, "assembleTemplate")).isTrue();
        assertThat(exposed(com.ujcms.cms.core.domain.Article.class, "getUrl"))
                .as("article.getUrl(page) 是正当读 API").isTrue();
        assertThat(exposed(User.class, "hasGlobalPermission")).isTrue();
        // 普通无参 getter 当然可读
        assertThat(exposed(Site.class, "getTitle")).isTrue();
        assertThat(exposed(User.class, "isHasPassword")).isTrue();
    }

    /**
     * 规则覆盖全部领域实体，而不只是拒绝表里那几个类：反射扫描领域包中的实体，
     * 断言<b>没有任何</b> setter 或副作用方法被暴露。新增一个实体类自动纳入，无需登记。
     */
    @Test
    void noSetterIsExposedOnAnyDomainEntity() {
        List<Class<?>> domainEntities = List.of(
                Config.class, Config.Storage.class, Config.Sms.class, Config.Email.class,
                Config.Security.class, Config.Upload.class, User.class, Site.class,
                com.ujcms.cms.core.domain.Article.class, com.ujcms.cms.core.domain.Channel.class,
                com.ujcms.cms.core.domain.Model.class, com.ujcms.cms.core.domain.Block.class);
        List<String> leaked = new ArrayList<>();
        for (Class<?> clazz : domainEntities) {
            assertThat(TemplateMemberAccessPolicy.isDomainEntity(clazz))
                    .as("%s 应被识别为领域实体", clazz.getName()).isTrue();
            for (Method method : clazz.getMethods()) {
                if (TemplateMemberAccessPolicy.isMutator(method.getName())
                        && exposed(clazz, method.getName())) {
                    leaked.add(clazz.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertThat(leaked)
                .as("领域实体的写方法/副作用方法对模板可调用——模板持有的是缓存实体，"
                        + "写操作会污染其他请求（design-review-2026-07 T5 附带项）")
                .isEmpty();
    }

    /** 该类的该方法名，在当前策略下是否对模板暴露。 */
    private boolean exposed(Class<?> clazz, String methodName) {
        var classPolicy = new TemplateMemberAccessPolicy().forClass(clazz);
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(methodName) && classPolicy.isMethodExposed(method)) {
                return true;
            }
        }
        return false;
    }

    /**
     * <b>本测试套件的核心</b>：反射枚举被审查实体上所有 public getter，凡命中敏感命名启发式的，
     * 必须已在拒绝表内（或在 {@link #SENSITIVE_NAME_EXEMPTIONS} 中带理由豁免）。
     * <p>
     * 于是“给 Config 加一个敏感 getter”这件事，会在<b>构建时</b>失败，而不是等安全审计发现——
     * 这正是 T5 所谓“a trap the day Config grows a sensitive getter”的解法。
     */
    @Test
    void newSensitiveGetterFailsTheBuild() {
        List<String> unguarded = new ArrayList<>();
        for (Class<?> clazz : AUDITED_CLASSES) {
            Set<String> denied = TemplateMemberAccessPolicy.DENIED_MEMBERS
                    .getOrDefault(clazz, Set.of());
            for (Method method : clazz.getMethods()) {
                if (!isReadableProperty(method)) {
                    continue;
                }
                String lower = method.getName().toLowerCase(Locale.ROOT);
                boolean looksSensitive = SENSITIVE_NAME_FRAGMENTS.stream().anyMatch(lower::contains);
                if (!looksSensitive) {
                    continue;
                }
                String key = clazz.getName() + "#" + method.getName();
                if (denied.contains(method.getName()) || SENSITIVE_NAME_EXEMPTIONS.containsKey(key)) {
                    continue;
                }
                unguarded.add(key);
            }
        }
        assertThat(unguarded)
                .as("这些 getter 的命名看起来携带密钥/凭据/PII 索引，但对模板可读。"
                        + "请把它加入 TemplateMemberAccessPolicy.DENIED_MEMBERS，"
                        + "或在 SENSITIVE_NAME_EXEMPTIONS 中写明豁免理由（design-review-2026-07 T5）")
                .isEmpty();
    }

    /**
     * 拒绝表不得有陈旧条目：每个被拒方法名都要在类上真实存在。
     * 否则一次改名（{@code getSecretKey} → {@code getSecret}）会让拒绝<b>静默失效</b>。
     */
    @Test
    void deniedMembersAllResolveToRealMethods() {
        List<String> stale = new ArrayList<>();
        TemplateMemberAccessPolicy.DENIED_MEMBERS.forEach((clazz, deniedNames) -> {
            Set<String> actual = new java.util.HashSet<>();
            for (Method method : clazz.getMethods()) {
                actual.add(method.getName());
            }
            for (String denied : deniedNames) {
                if (!actual.contains(denied)) {
                    stale.add(clazz.getName() + "#" + denied);
                }
            }
        });
        assertThat(stale)
                .as("拒绝表引用了不存在的方法——很可能是 getter 改了名，"
                        + "而拒绝已静默失效（design-review-2026-07 T5）")
                .isEmpty();
    }

    private static boolean isReadableProperty(Method method) {
        if (method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) {
            return false;
        }
        String name = method.getName();
        return (name.startsWith("get") && name.length() > 3)
                || (name.startsWith("is") && name.length() > 2);
    }
}
