package com.ujcms.cms.core.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 验证 AI 就绪判定：服务商解析（含回落默认服务商）、翻译服务商默认路径（大模型或机器翻译引擎凭证）。
 * 站点设置页“开关不生效”提示与 MtClient 引擎门槛共用这套规则
 */
class ConfigAiReadinessTest {

    private static Config.Ai aiWithProvider(String code) {
        Config.Ai ai = new Config.Ai();
        Config.Ai.Provider provider = new Config.Ai.Provider();
        provider.setCode(code);
        provider.setName(code);
        provider.setBaseUrl("http://localhost/v1");
        provider.setModel("m");
        ai.getProviders().add(provider);
        return ai;
    }

    @Test
    void resolvesProviderFallsBackToDefault() {
        Config.Ai ai = aiWithProvider("p1");
        assertFalse(ai.resolvesProvider(null), "未设默认服务商时空代码不可解析");
        ai.setDefaultProvider("p1");
        assertTrue(ai.resolvesProvider(null), "空代码回落到默认服务商");
        assertTrue(ai.resolvesProvider(""), "空串同样回落到默认服务商");
        assertTrue(ai.resolvesProvider("p1"));
        assertFalse(ai.resolvesProvider("missing"), "悬空代码不回落到默认服务商");
    }

    @Test
    void translationProviderUsesLlmResolutionWhenNotMt() {
        Config.Ai ai = aiWithProvider("p1");
        assertFalse(ai.resolvesTranslationProvider(), "无默认服务商且未指定翻译服务商");
        ai.setDefaultProvider("p1");
        assertTrue(ai.resolvesTranslationProvider(), "翻译服务商留空回落到默认服务商");
        ai.setTranslationProvider("missing");
        assertFalse(ai.resolvesTranslationProvider());
    }

    @Test
    void translationProviderUsesMtCredentialsWhenMt() {
        Config.Ai ai = new Config.Ai();
        ai.setTranslationProvider(Config.Ai.MT_BAIDU);
        assertFalse(ai.resolvesTranslationProvider(), "百度凭证为空");
        ai.getMt().setBaiduAppId("id");
        assertFalse(ai.resolvesTranslationProvider(), "百度需要 APP ID 与密钥两者");
        ai.getMt().setBaiduSecret("secret");
        assertTrue(ai.resolvesTranslationProvider());

        ai.setTranslationProvider(Config.Ai.MT_DEEPL);
        assertFalse(ai.resolvesTranslationProvider());
        ai.getMt().setDeeplAuthKey("key:fx");
        assertTrue(ai.resolvesTranslationProvider());

        ai.setTranslationProvider(Config.Ai.MT_YOUDAO);
        ai.getMt().setYoudaoAppKey("k");
        assertFalse(ai.resolvesTranslationProvider(), "有道需要应用ID与应用密钥两者");
        ai.getMt().setYoudaoSecret("s");
        assertTrue(ai.resolvesTranslationProvider());
    }

    @Test
    void mtIsConfiguredRejectsUnknownCode() {
        Config.Ai.Mt mt = new Config.Ai.Mt();
        mt.setDeeplAuthKey("key");
        assertFalse(mt.isConfigured("mt:unknown"));
        assertFalse(mt.isConfigured("p1"), "大模型服务商代码不是机器翻译引擎");
    }
}
