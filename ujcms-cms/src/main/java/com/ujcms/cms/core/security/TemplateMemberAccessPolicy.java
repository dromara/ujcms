package com.ujcms.cms.core.security;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import freemarker.ext.beans.ClassMemberAccessPolicy;
import freemarker.ext.beans.DefaultMemberAccessPolicy;
import freemarker.ext.beans.MemberAccessPolicy;
import freemarker.template.Configuration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 模板可达面收敛策略（design-review-2026-07 T5 / P3-7）。管两件事：
 * <ol>
 * <li><b>密钥不可读</b>——凭据、口令散列、PII 盲索引从对象包装器摘除（下述 T5 主线）；</li>
 * <li><b>领域实体只读</b>——模板不得调用 setter 或有副作用的方法。模板拿到的是 MyBatis
 * 二级缓存里的<b>那个</b>实体对象，{@code [#assign x = config.setSmsSettings('…')]} 会越过请求边界
 * 污染其他请求看到的状态；{@code config.email.sendMail(…)} 更是能以站点名义发任意邮件、
 * {@code config.uploadStorage.getFileHandler(…)} 授出存储读写句柄。参考主题的 98 个模板文件里
 * 对实体<b>零</b>方法调用（只读属性），因此整体拒绝无正当损失。见 {@link #isMutator(String)}。</li>
 * </ol>
 * <p>
 * 前台模板取到的是<b>领域实体本身</b>（{@code Frontends} 把 {@code Site#getConfig()}、{@code Site}、
 * {@code User} 直接写进数据模型），而 FreeMarker 的 BeansWrapper 会暴露<b>每一个 public getter</b>。
 * {@code @JsonIgnore} 只约束 Jackson 序列化，对模板属性访问<b>毫无作用</b>——因此
 * {@link Sm4StringTypeHandler}（同包）在读取时解密出的凭据与个人信息，会原文落在实体上并对模板可见。
 * 复核确认三条泄露路径均已成立：
 * <ol>
 * <li>属性链：{@code ${config.templateStorage.secretKey}}、{@code ${config.sms.accessKeySecret}}、
 * {@code ${config.email.password}}——也可经 {@code site.config.*}、{@code defaultSite.config.*}
 * 及 {@code [@SiteList]} 等任何产出 Site 的标签绕行；</li>
 * <li>密文列原文：{@code ${config.smsSettings}} 等 {@code *Settings} 字符串即解密后的完整 JSON；</li>
 * <li>Lombok {@code @Data} 的 {@code toString()}：{@code ${config}} 一次性打印全部密钥，
 * {@code ${user}} 打印口令散列与解密后的手机号/邮箱（{@code ${article.user}} 同理）。</li>
 * </ol>
 * 本策略在 FreeMarker 层（且<b>仅在</b> FreeMarker 层——JSON 响应不受影响）把上述成员从对象包装器
 * 中摘除：被拒成员在模板中表现为“不存在”，{@code ${config.sms.secretKey}} 抛
 * {@code InvalidReferenceException}，{@code ${config}} 输出 {@code [toString not exposed]}。
 * <p>
 * 两种失败形态（都安全，语义不同，实测确认）：被拒的<b>属性读取</b>是<b>响亮失败</b>
 * （抛 {@code InvalidReferenceException}，绝不静默返回空串）；被拒的<b>方法调用</b>
 * （如 {@code ${site.setName('x')}}）则是<b>静默空操作</b>——FreeMarker 对缺失成员的调用返回
 * {@code TemplateModel.NOTHING}，渲染为空串且<b>不会执行该方法</b>。对写操作而言“什么都不发生”
 * 正是想要的结果。判断成员是否可达请用 {@code ${site.setName??}}（=false），不要依赖调用是否报错。
 * 因为拒绝以<b>类 + 方法名</b>为单位，所有通往同一实体的路径（全局变量、实体图链、指令返回值）一并覆盖，
 * 无需在每个注入点维护视图对象。
 * <p>
 * 策略<b>叠加</b>在 FreeMarker 自带的 {@link DefaultMemberAccessPolicy} 之上（后者拦截
 * {@code getClassLoader}、{@code System.exit} 一类危险成员），不做替换——否则会丢掉内置防护。
 * <p>
 * 未列入拒绝表的成员一律保持可读：参考主题实际使用的 {@code config.register.*}、
 * {@code config.security.password*}、{@code config.upload.*}、{@code config.greyStyle}、
 * {@code config.commercialTier}、{@code config.customs}、{@code site.*}、{@code user.username}
 * 等渲染结果逐字节不变。
 * <h2>边界：{@code user.mobile} / {@code user.email} 有意保留</h2>
 * 二者在参考主题的会员“账号设置”页有正当用途（显示当前会员本人的“原邮箱”“原手机号”），而成员级策略
 * 无法区分“本人”与“他人”（同一个 {@code User} 类）。禁掉会直接破坏已发布的功能且无替代读法，故保留，
 * 并把“主题打印他人 PII”明确记为主题作者的责任——主题代码本就是受信代码（见
 * {@code docs/guide/theme-ops.zh.md} §1）。口令散列、历史口令、盲索引与 toString 转储
 * 则没有任何正当模板用途，一律拒绝。
 *
 * @author UJCMS
 * @see com.ujcms.cms.core.ContextConfig#afterPropertiesSet()
 */
public class TemplateMemberAccessPolicy implements MemberAccessPolicy {
    /**
     * {@code toString()} —— Lombok {@code @Data} 在 {@code GeneratedConfig} / {@code GeneratedUser}
     * 上生成的转储，逐字段打印，包含解密后的凭据与个人信息。
     */
    public static final String TO_STRING = "toString";

    /**
     * 类 → 被拒绝的方法名。以<b>方法名</b>（而非声明类）匹配：getter 可能声明在 Lombok 生成的父类上
     * （如 {@code GeneratedConfig#getSmsSettings()}），而 BeansWrapper 是按<b>实体类</b>做内省的。
     * <p>
     * 守卫测试 {@code TemplateMemberAccessPolicyTest} 会反射校验本表：既确认每个条目都对应真实方法
     * （改名不会静默失效），也扫描这些类上所有“看起来敏感”的 getter（{@code secret}/{@code password}/
     * {@code accessKey}/{@code token}/{@code credential}/{@code *Settings} …）必须在表内——
     * 于是新增一个敏感 getter 会<b>让构建失败</b>，而不是等下一次安全审计。
     */
    public static final Map<Class<?>, Set<String>> DENIED_MEMBERS = buildDeniedMembers();

    private static Map<Class<?>, Set<String>> buildDeniedMembers() {
        Map<Class<?>, Set<String>> denied = new LinkedHashMap<>(8);
        // 全局配置：整棵凭据子树 + 加密列原文 + toString 转储
        denied.put(Config.class, methods(
                // 凭据子对象访问器：摘掉这几个，Storage/Sms/Email/Ai 实例根本到不了模板
                "getSms", "getEmail", "getUploadStorage", "getHtmlStorage", "getTemplateStorage",
                // AI 设置含服务商明文密钥（providers[].apiKey），整棵子树对模板不可达
                "getAi",
                // 第三方登录设置含各服务商的应用密钥；模板只读 config.oauthProviders（可用服务商代码，无凭据）
                "getOauth",
                // 验证码设置含各厂商密钥；浏览器只经 GET /api/captcha 拿到出题所需的公开标识
                "getCaptcha",
                // 落库前 SM4-GCM 加密、读取时由 Sm4StringTypeHandler 解密的原文 JSON
                "getSmsSettings", "getEmailSettings",
                "getUploadStorageSettings", "getHtmlStorageSettings", "getTemplateStorageSettings",
                "getAiSettings",
                "getOauthSettings",
                "getCaptchaSettings",
                TO_STRING));
        // 凭据子对象自身：纵深防御。上面已断了 config 这条路，这里保证即便日后有别的代码
        // 把 Storage/Sms/Email/Ai.Provider 实例塞进数据模型，密钥依然不可读。
        denied.put(Config.Storage.class, methods(
                "getAccessKey", "getSecretKey", "getUsername", "getPassword"));
        denied.put(Config.Sms.class, methods(
                "getAccessKeyId", "getAccessKeySecret", "getSecretId", "getSecretKey"));
        denied.put(Config.Email.class, methods(
                "getUsername", "getPassword"));
        denied.put(Config.Ai.Provider.class, methods("getApiKey"));
        denied.put(Config.Oauth.Client.class, methods("getClientSecret"));
        denied.put(Config.Captcha.Geetest.class, methods("getCaptchaKey"));
        denied.put(Config.Captcha.Tencent.class, methods("getAppSecretKey", "getSecretKey"));
        denied.put(Config.Captcha.Aliyun.class, methods("getAccessKeySecret"));
        denied.put(Config.Captcha.Yidun.class, methods("getSecretKey"));
        denied.put(Config.Captcha.Turnstile.class, methods("getSecretKey"));
        // 站点 GEO 设置：百度推送接口地址带搜索资源平台 token（凭据），原文 JSON 同样含它；
        // 模板只需 site.geo.jsonLdEnabled 一类开关，整段原文没有正当模板用途
        denied.put(Site.Geo.class, methods("getBaiduPushUrl"));
        denied.put(Site.class, methods("getGeoSettings"));
        // 用户：口令散列、历史口令、PII 盲索引、toString 转储。
        // 注意 getMobile/getEmail 有意<b>不</b>拒绝——见类注释“边界”一节。
        denied.put(User.class, methods(
                "getPassword",
                "getHistoryPassword", "getHistoryPasswordList",
                "getMobileIndex", "getEmailIndex",
                TO_STRING));
        return Collections.unmodifiableMap(denied);
    }

    private static Set<String> methods(String... names) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(java.util.Arrays.asList(names)));
    }

    /**
     * 领域实体所在包。这些类是<b>共享的缓存实体</b>（MyBatis 二级缓存），模板拿到的就是缓存里的那个对象——
     * 因此模板对它们只应有<b>只读</b>视图：任何写操作都会越过请求边界，污染其他请求看到的状态。
     */
    private static final String[] DOMAIN_PACKAGES = {
            "com.ujcms.cms.core.domain", "com.ujcms.cms.ext.domain"};

    /**
     * 领域实体上<b>有副作用或授出能力</b>的非 setter 方法。setter 由
     * {@link #isJavaBeansSetter(String)} 按命名规则整体拒绝（351 个，无一有正当模板用途），
     * 这里补上那些不叫 {@code setXxx} 却同样不该让模板调用的：
     * <ul>
     * <li>{@code sendMail} —— {@code Config.Email} 上的<b>发信</b>方法。模板一旦拿到 Email 实例即可
     * 以站点名义发任意邮件（当前 {@code config.email} 已被拒绝，此处为纵深防御）。</li>
     * <li>{@code getFileHandler} —— {@code Config.Storage} 上返回 {@code FileHandler}，等于授出
     * 文件系统/对象存储的读写句柄。</li>
     * <li>{@code disassembleCustoms} / {@code disassembleMap} / {@code disassemble} /
     * {@code adjustStatus} / {@code addHistoryPassword} / {@code normalizeVisibility}
     * —— 就地改写实体状态。</li>
     * <li>{@code fetchNextVal} / {@code copyToCache} —— {@code Seq} 上推进数据库序列，有持久化副作用。</li>
     * </ul>
     * 只读辅助方法（{@code Site#hasMobileTheme}、{@code Site#assembleTemplate}、
     * {@code User#hasGlobalPermission}、以及 49 个带参只读 getter 如 {@code article.getUrl(page)}、
     * {@code site.getBasePath(path)}）<b>保持可用</b>——它们是主题作者正当的读 API。
     */
    private static final Set<String> SIDE_EFFECTING_METHODS = Set.of(
            "sendMail", "getFileHandler",
            "disassembleCustoms", "disassembleMap", "disassemble",
            "adjustStatus", "addHistoryPassword", "normalizeVisibility",
            "fetchNextVal", "copyToCache");

    /** 领域实体（模板对其只有只读视图）。 */
    static boolean isDomainEntity(Class<?> clazz) {
        Package pkg = clazz.getPackage();
        if (pkg == null) {
            return false;
        }
        String name = pkg.getName();
        for (String domain : DOMAIN_PACKAGES) {
            if (name.equals(domain)) {
                return true;
            }
        }
        return false;
    }

    /**
     * JavaBeans setter 命名形态（{@code setXxx}）。刻意不匹配 {@code settle}、{@code settings} 一类
     * 恰好以 "set" 开头的普通方法——只认第 4 个字符为大写的规范 setter。
     */
    static boolean isJavaBeansSetter(String methodName) {
        return methodName.length() > 3 && methodName.startsWith("set")
                && Character.isUpperCase(methodName.charAt(3));
    }

    /** 该方法是否为领域实体上的写操作 / 能力授出（模板一律不可调用）。 */
    static boolean isMutator(String methodName) {
        return isJavaBeansSetter(methodName) || SIDE_EFFECTING_METHODS.contains(methodName);
    }

    private final MemberAccessPolicy delegate;

    public TemplateMemberAccessPolicy() {
        this(DefaultMemberAccessPolicy.getInstance(Configuration.VERSION_2_3_31));
    }

    public TemplateMemberAccessPolicy(MemberAccessPolicy delegate) {
        this.delegate = delegate;
    }

    @Override
    public ClassMemberAccessPolicy forClass(Class<?> clazz) {
        ClassMemberAccessPolicy delegated = delegate.forClass(clazz);
        Set<String> denied = DENIED_MEMBERS.getOrDefault(clazz, Collections.emptySet());
        // 领域实体：模板只读（拒绝 setter 与有副作用的方法）
        boolean readOnly = isDomainEntity(clazz);
        if (denied.isEmpty() && !readOnly) {
            return delegated;
        }
        return new DenyingClassMemberAccessPolicy(delegated, denied, readOnly);
    }

    /**
     * {@code false} 表示 {@code toString()} 也要走 {@link ClassMemberAccessPolicy#isMethodExposed}。
     * 这是让 {@code ${config}} / {@code ${user}} 不再打印 Lombok 转储的开关：FreeMarker 的
     * {@code StringModel#getAsString()} 只在本方法返回 {@code false} 时才去查每个类的 toString 是否放行。
     * 未列入 {@link #DENIED_MEMBERS} 的类不受影响，{@code toString()} 照常可用。
     */
    @Override
    public boolean isToStringAlwaysExposed() {
        return false;
    }

    private static class DenyingClassMemberAccessPolicy implements ClassMemberAccessPolicy {
        private final ClassMemberAccessPolicy delegate;
        private final Set<String> deniedMethodNames;
        private final boolean readOnly;

        DenyingClassMemberAccessPolicy(ClassMemberAccessPolicy delegate, Set<String> deniedMethodNames,
                                       boolean readOnly) {
            this.delegate = delegate;
            this.deniedMethodNames = deniedMethodNames;
            this.readOnly = readOnly;
        }

        @Override
        public boolean isMethodExposed(Method method) {
            String name = method.getName();
            if (deniedMethodNames.contains(name)) {
                return false;
            }
            if (readOnly && isMutator(name)) {
                return false;
            }
            return delegate.isMethodExposed(method);
        }

        @Override
        public boolean isConstructorExposed(Constructor<?> constructor) {
            return delegate.isConstructorExposed(constructor);
        }

        @Override
        public boolean isFieldExposed(Field field) {
            // 领域实体的 public 字段同样不可写；BeansWrapper 默认也不暴露实例字段，此处保持委托语义
            return delegate.isFieldExposed(field);
        }
    }
}
