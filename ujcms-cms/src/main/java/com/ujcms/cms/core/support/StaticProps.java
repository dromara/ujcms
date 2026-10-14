package com.ujcms.cms.core.support;

/**
 * 静态属性的配置文件
 *
 * @author PONY
 */
public class StaticProps {
    /**
     * 敏感数据（短信/邮件/对象存储凭据等）入库加密所用 SM4 密钥的内置默认值（16 字符）。
     * 默认值可直接使用，能满足一般需求；仅在安全要求较高时，于 {@code application.yaml}
     * 配置自定义 {@code ujcms.data-secret-key} 覆盖。
     */
    public static final String DEFAULT_DATA_SECRET_KEY = "0123456789ABCDEF";
    /** SM4-128 密钥长度（字符）。 */
    public static final int DATA_SECRET_KEY_LENGTH = 16;
    /**
     * 密码胡椒（{@code ujcms.password-pepper}）的内置示例值。开启 {@code ujcms.security.reject-default-key}
     * 后，若仍为此值（或为空）将启动失败（Phase 5）。
     */
    public static final String DEFAULT_PASSWORD_PEPPER = "0.1.2.3.4.5.6.7.8.9.a.b.c.d.e.f.";
    private static String dataSecretKey = DEFAULT_DATA_SECRET_KEY;

    public static String getDataSecretKey() {
        return dataSecretKey;
    }

    public static void setDataSecretKey(String dataSecretKey) {
        StaticProps.dataSecretKey = dataSecretKey;
    }

    /**
     * 三权分立（等保三级 访问控制）开关。开启后超级管理员（id=1）被中和：{@link com.ujcms.cms.core.domain.User#isRoot()}
     * 返回 {@code false}（消除全局权限旁路），且 id=1 账号被强制停用且不可重新启用。由
     * {@link SecurityProperties#setSeparationOfDuties(boolean)} 在配置绑定时下发到此处，供无法注入的领域层静态读取。
     */
    private static boolean separationOfDuties = false;

    public static boolean isSeparationOfDuties() {
        return separationOfDuties;
    }

    public static void setSeparationOfDuties(boolean separationOfDuties) {
        StaticProps.separationOfDuties = separationOfDuties;
    }

    private StaticProps() {
        throw new IllegalStateException("Utility class");
    }
}
