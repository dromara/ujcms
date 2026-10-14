package com.ujcms.cms.core.support;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安装清单的环境变量写法（docker/application.yaml 注释、docs/guide/install-flow.zh.md §三 所列）确实按 Spring 宽松绑定
 * 落到 {@link Props.Install}：注册开关、第三方登录凭据、AI 服务商与机器翻译引擎。容器部署只靠这些变量名，名字写错不会报错、只会静默不生效。
 */
class InstallManifestEnvBindingTest {

    private static Props.Install bind(Map<String, Object> env) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env));
        ConfigurationPropertySources.attach(environment);
        return Binder.get(environment).bind("ujcms.install", Props.Install.class).orElseGet(Props.Install::new);
    }

    @Test
    void oauthCredentialsAndRegisterSwitchBindFromEnvironmentVariables() {
        Props.Install install = bind(Map.of(
                "UJCMS_INSTALL_REGISTER_ENABLED", "true",
                "UJCMS_INSTALL_OAUTH_WEIXIN_CLIENTID", "wx-app-id",
                "UJCMS_INSTALL_OAUTH_WEIXIN_CLIENTSECRET", "wx-secret",
                "UJCMS_INSTALL_OAUTH_QQ_CLIENTID", "qq-id",
                "UJCMS_INSTALL_OAUTH_QQ_CLIENTSECRET", "qq-key",
                // 留空（密钥库未配置该服务商）：绑定为空串，安装步骤据此跳过
                "UJCMS_INSTALL_OAUTH_WEIBO_CLIENTID", "",
                "UJCMS_INSTALL_OAUTH_WEIBO_CLIENTSECRET", ""));

        assertEquals(Boolean.TRUE, install.getRegister().getEnabled());
        assertEquals("wx-app-id", install.getOauth().getWeixin().getClientId());
        assertEquals("wx-secret", install.getOauth().getWeixin().getClientSecret());
        assertEquals("qq-id", install.getOauth().getQq().getClientId());
        assertEquals("qq-key", install.getOauth().getQq().getClientSecret());
        assertEquals("", install.getOauth().getWeibo().getClientId());
        assertEquals("", install.getOauth().getWeibo().getClientSecret());
    }

    @Test
    void aiProviderBindsFromEnvironmentVariables() {
        Props.Install install = bind(Map.of(
                "UJCMS_INSTALL_AI_PROVIDER_NAME", "DeepSeek",
                "UJCMS_INSTALL_AI_PROVIDER_BASEURL", "https://api.deepseek.com/v1",
                "UJCMS_INSTALL_AI_PROVIDER_APIKEY", "sk-test",
                "UJCMS_INSTALL_AI_PROVIDER_MODEL", "deepseek-chat",
                "UJCMS_INSTALL_AI_PROVIDER_EXTRAPARAMS", "{\"thinking\":{\"type\":\"disabled\"}}"));

        Props.Install.AiProvider provider = install.getAi().getProvider();
        assertTrue(provider.declared());
        assertEquals("DeepSeek", provider.getName());
        assertEquals("https://api.deepseek.com/v1", provider.getBaseUrl());
        assertEquals("sk-test", provider.getApiKey());
        assertEquals("deepseek-chat", provider.getModel());
        assertEquals("{\"thinking\":{\"type\":\"disabled\"}}", provider.getExtraParams());
    }

    @Test
    void baiduTranslateCredentialsBindFromEnvironmentVariables() {
        Props.Install install = bind(Map.of(
                "UJCMS_INSTALL_AI_MT_BAIDU_APPID", "20260101000000001",
                "UJCMS_INSTALL_AI_MT_BAIDU_SECRET", "baidu-secret"));

        Props.Install.AiMtBaidu baidu = install.getAi().getMt().getBaidu();
        assertTrue(baidu.declared());
        assertEquals("20260101000000001", baidu.getAppId());
        assertEquals("baidu-secret", baidu.getSecret());
        assertFalse(install.getAi().getProvider().declared());
    }

    @Test
    void blankAiVariablesDeclareNoProvider() {
        // 留空（密钥库未配置 AI 服务商）：绑定为空串，安装步骤据此跳过
        Props.Install install = bind(Map.of(
                "UJCMS_INSTALL_AI_PROVIDER_NAME", "",
                "UJCMS_INSTALL_AI_PROVIDER_BASEURL", "",
                "UJCMS_INSTALL_AI_PROVIDER_APIKEY", "",
                "UJCMS_INSTALL_AI_PROVIDER_MODEL", "",
                "UJCMS_INSTALL_AI_MT_BAIDU_APPID", "",
                "UJCMS_INSTALL_AI_MT_BAIDU_SECRET", ""));

        assertFalse(install.getAi().getProvider().declared());
        assertFalse(install.getAi().getMt().getBaidu().declared());
    }

    @Test
    void absentVariablesLeaveTheManifestAtItsDefaults() {
        Props.Install install = bind(Map.of("UJCMS_INSTALL_AUTO", "true"));

        assertEquals(true, install.isAuto());
        assertNull(install.getRegister().getEnabled());
        assertEquals("", install.getOauth().getWeixin().getClientId());
        assertFalse(install.getAi().getProvider().declared());
        assertFalse(install.getAi().getMt().getBaidu().declared());
    }
}
