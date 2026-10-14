package com.ujcms.cms.core.security;

import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.SecurityProperties;
import com.ujcms.cms.core.support.StaticProps;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * 拒绝默认密钥（密评 密钥管理 / G8，Phase 5）。
 *
 * <p>当 {@code ujcms.security.reject-default-key} 开启（{@code level3} Profile 即开启）时，启动校验
 * {@code ujcms.data-secret-key} 与 {@code ujcms.password-pepper} 是否仍为内置默认/空值；若是则**启动失败**
 * （fail-loud），强制三级部署改用自定义密钥。默认关闭——开箱即用仍可使用内置默认密钥（符合 opt-in 设计）。
 *
 * <p>本组件只做配置校验、不修改任何状态，故在启动期执行是安全的（区别于“启动时重建昂贵状态”）。
 *
 * @author PONY
 * @see SecurityProperties#isRejectDefaultKey()
 */
@Component
public class DefaultKeyGuard implements InitializingBean {
    private final SecurityProperties securityProperties;
    private final Props props;

    public DefaultKeyGuard(SecurityProperties securityProperties, Props props) {
        this.securityProperties = securityProperties;
        this.props = props;
    }

    @Override
    public void afterPropertiesSet() {
        // 数据密钥的有效值在 StaticProps（Props 绑定时下发；留空则保留内置默认）。胡椒直接取 Props（编码器即用此值）。
        check(securityProperties.isRejectDefaultKey(), StaticProps.getDataSecretKey(), props.getPasswordPepper());
    }

    /** 校验逻辑。包级可见以便单元测试。{@code reject} 关闭时不做任何校验。 */
    static void check(boolean reject, String dataSecretKey, String passwordPepper) {
        if (!reject) {
            return;
        }
        if (StringUtils.isBlank(dataSecretKey) || StaticProps.DEFAULT_DATA_SECRET_KEY.equals(dataSecretKey)) {
            throw new IllegalStateException("ujcms.security.reject-default-key 已开启，但 ujcms.data-secret-key "
                    + "仍为内置默认值，请改为自定义的 16 字符密钥（见 runbook §2.2）。");
        }
        if (StringUtils.isBlank(passwordPepper) || StaticProps.DEFAULT_PASSWORD_PEPPER.equals(passwordPepper)) {
            throw new IllegalStateException("ujcms.security.reject-default-key 已开启，但 ujcms.password-pepper "
                    + "仍为内置默认/空值，请改为自定义随机值（建议 ≥32 字符）。注意：变更密码胡椒会使历史口令失效，"
                    + "统一重置流程见 runbook §2.2。");
        }
    }
}
