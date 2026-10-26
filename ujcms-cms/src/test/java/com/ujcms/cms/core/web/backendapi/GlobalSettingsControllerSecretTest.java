package com.ujcms.cms.core.web.backendapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.ModelService;
import com.ujcms.cms.core.service.ShortMessageService;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.captcha.CaptchaTokenService;
import com.ujcms.common.web.exception.LogicException;

/**
 * 验证管理端凭据“只写不回显”：读取脱敏、保存时表单留空则沿用库中原值。
 */
class GlobalSettingsControllerSecretTest {

    private GlobalSettingsController newController(Config config) {
        ConfigService service = mock(ConfigService.class);
        when(service.getUnique()).thenReturn(config);
        return new GlobalSettingsController(mock(HtmlSanitizeService.class), mock(ModelService.class),
                mock(ShortMessageService.class), service, mock(CaptchaTokenService.class), mock(Props.class));
    }

    private Config configWithSms() {
        Config config = new Config();
        Config.Sms sms = new Config.Sms();
        sms.setProvider(Config.Sms.PROVIDER_ALIYUN);
        sms.setAccessKeyId("AKID-visible");
        sms.setAccessKeySecret("AKSECRET-top-secret");
        sms.setSecretKey("SK-top-secret");
        sms.setSignName("我的签名");
        config.setSms(sms);
        return config;
    }

    @Test
    void smsServiceCannotBeDisabledWhileTwoFactorDependsOnIt() {
        Config config = configWithSms();
        // getSecurity() 在未设置时每次返回新对象，须经 setSecurity 写回
        Config.Security security = new Config.Security();
        security.setTwoFactor(true);
        config.setSecurity(security);
        Config.Sms none = new Config.Sms();
        none.setProvider(Config.Sms.PROVIDER_NONE);

        // 双因子认证开启期间停用短信服务，所有人都将无法登录
        LogicException e = assertThrows(LogicException.class, () -> newController(config).updateSms(none));
        assertEquals("error.twoFactorRequiresSms", e.getMessage());
        assertEquals(Config.Sms.PROVIDER_ALIYUN, config.getSms().getProvider(), "拒绝后原设置保持不变");
    }

    @Test
    void showSmsMasksSecretsButKeepsIdentifiers() {
        Config.Sms masked = newController(configWithSms()).showSms();
        assertEquals("", masked.getAccessKeySecret(), "accessKeySecret 必须脱敏（不回传浏览器）");
        assertEquals("", masked.getSecretKey(), "secretKey 必须脱敏");
        assertEquals("AKID-visible", masked.getAccessKeyId(), "标识类字段保留可见");
        assertEquals("我的签名", masked.getSignName(), "非密钥字段保留");
    }

    @Test
    void showSmsDoesNotMutateStoredConfig() {
        Config config = configWithSms();
        newController(config).showSms();
        assertEquals("AKSECRET-top-secret", config.getSms().getAccessKeySecret(),
                "脱敏只作用于返回副本，缓存的真实配置不应被改动");
    }

    @Test
    void updateSmsPreservesBlankSecrets() {
        Config config = configWithSms();
        Config.Sms bean = new Config.Sms();
        bean.setProvider(Config.Sms.PROVIDER_ALIYUN);
        bean.setAccessKeyId("AKID-visible");
        bean.setAccessKeySecret("");
        bean.setSecretKey("");
        bean.setSignName("新签名");

        newController(config).updateSms(bean);

        assertEquals("AKSECRET-top-secret", config.getSms().getAccessKeySecret(), "留空时沿用库中原密钥");
        assertEquals("SK-top-secret", config.getSms().getSecretKey(), "留空时沿用库中原密钥");
        assertEquals("新签名", config.getSms().getSignName(), "非密钥字段按表单更新");
    }

    @Test
    void updateSmsAppliesNewSecretWhenProvided() {
        Config config = configWithSms();
        Config.Sms bean = new Config.Sms();
        bean.setProvider(Config.Sms.PROVIDER_ALIYUN);
        bean.setAccessKeySecret("AKSECRET-new");

        newController(config).updateSms(bean);

        assertEquals("AKSECRET-new", config.getSms().getAccessKeySecret(), "提供新值时覆盖原密钥");
    }

    @Test
    void updateBaseKeepsTheCredentialColumnsTheRequestNeverCarries() throws Exception {
        // 基础设置整行保存：请求体即控制台取回的 Config JSON，其中不含凭据类设置（sms / ai / oauth 及其原始列均不序列化）。
        // Entities.copy 连 null 一并拷贝，这些原始列必须在排除名单里，否则保存基础设置会把它们清空
        Config config = configWithSms();
        Config.Ai ai = new Config.Ai();
        ai.setEnabled(true);
        config.setAi(ai);
        Config.Oauth oauth = new Config.Oauth();
        oauth.getWeixin().setEnabled(true);
        oauth.getWeixin().setClientId("wx-app-id");
        oauth.getWeixin().setClientSecret("wx-secret");
        config.setOauth(oauth);
        Config bean = Constants.MAPPER.readValue(Constants.MAPPER.writeValueAsString(config), Config.class);

        newController(config).updateBase(bean);

        assertEquals("wx-secret", config.getOauth().getWeixin().getClientSecret(), "保存基础设置不得清空第三方登录设置");
        assertEquals(java.util.List.of("weixin"), config.getOauthProviders());
        assertEquals("AKSECRET-top-secret", config.getSms().getAccessKeySecret(), "保存基础设置不得清空短信设置");
        assertEquals(true, config.getAi().isEnabled(), "保存基础设置不得清空 AI 设置");
    }
}
