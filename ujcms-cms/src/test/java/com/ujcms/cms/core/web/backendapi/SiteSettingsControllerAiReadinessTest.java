package com.ujcms.cms.core.web.backendapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.SiteService;

/**
 * 验证站点 AI 功能页的全局就绪状态接口：只回传布尔值，各功能按各自服务商（含回落默认）判定
 */
class SiteSettingsControllerAiReadinessTest {

    private static SiteSettingsController newController(Config.Ai ai) {
        Config config = new Config();
        config.setAi(ai);
        ConfigService configService = mock(ConfigService.class);
        when(configService.getUnique()).thenReturn(config);
        return new SiteSettingsController(mock(SiteService.class), configService);
    }

    private static Config.Ai aiWithProviders(String... codes) {
        Config.Ai ai = new Config.Ai();
        for (String code : codes) {
            Config.Ai.Provider provider = new Config.Ai.Provider();
            provider.setCode(code);
            provider.setName(code);
            provider.setBaseUrl("http://localhost/v1");
            provider.setModel("m");
            ai.getProviders().add(provider);
        }
        return ai;
    }

    @Test
    void disabledWithoutProvidersIsAllFalse() {
        SiteSettingsController.AiReadinessResult result = newController(new Config.Ai()).aiReadiness();
        assertFalse(result.enabled());
        assertFalse(result.defaultProvider());
        assertFalse(result.writingProvider());
        assertFalse(result.moderationProvider());
        assertFalse(result.translationProvider());
    }

    @Test
    void overridesResolveIndependentlyOfDefault() {
        Config.Ai ai = aiWithProviders("writer", "judge");
        ai.setEnabled(true);
        ai.setWritingProvider("writer");
        ai.setModerationProvider("judge");
        ai.setTranslationProvider(Config.Ai.MT_DEEPL);
        ai.getMt().setDeeplAuthKey("key");
        SiteSettingsController.AiReadinessResult result = newController(ai).aiReadiness();
        assertTrue(result.enabled());
        assertFalse(result.defaultProvider(), "默认服务商未设置");
        assertTrue(result.writingProvider(), "写作服务商单独指定时不依赖默认服务商");
        assertTrue(result.moderationProvider());
        assertTrue(result.translationProvider(), "机器翻译引擎凭证完整即可用");
    }

    @Test
    void blankOverridesFallBackToDefault() {
        Config.Ai ai = aiWithProviders("p1");
        ai.setEnabled(true);
        ai.setDefaultProvider("p1");
        SiteSettingsController.AiReadinessResult result = newController(ai).aiReadiness();
        assertTrue(result.defaultProvider());
        assertTrue(result.writingProvider());
        assertTrue(result.moderationProvider());
        assertTrue(result.translationProvider());
    }
}
