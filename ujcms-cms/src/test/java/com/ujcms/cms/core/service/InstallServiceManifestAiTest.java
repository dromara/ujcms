package com.ujcms.cms.core.service;

import com.ujcms.cms.core.composition.ThemeSeedOrchestrator;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.mapper.ConfigMapper;
import com.ujcms.cms.core.mapper.UserExtMapper;
import com.ujcms.cms.core.mapper.UserMapper;
import com.ujcms.cms.core.mapper.UserRoleMapper;
import com.ujcms.cms.core.marketplace.MarketplaceInstallService;
import com.ujcms.cms.core.support.FirstInitSeeder;
import com.ujcms.cms.core.support.InstallTokenFile;
import com.ujcms.cms.core.support.Props;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerProperties;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 安装清单的 AI 服务商与机器翻译引擎步骤（{@code ujcms.install.ai.provider} / {@code ujcms.install.ai.mt}）：
 * {@link InstallService#configureAi} / {@link InstallService#configureAiMt}。
 */
class InstallServiceManifestAiTest {
    private Config config;
    private ConfigService configService;
    private InstallService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        config = new Config();
        configService = mock(ConfigService.class);
        when(configService.getUnique()).thenReturn(config);
        ObjectProvider<InstallSitePort> portProvider = mock(ObjectProvider.class);
        ObjectProvider<FirstInitSeeder> seederProvider = mock(ObjectProvider.class);
        service = new InstallService(mock(ConfigMapper.class), configService, mock(SiteService.class),
                mock(UserService.class), mock(RoleService.class), mock(UserMapper.class),
                mock(UserExtMapper.class), mock(UserRoleMapper.class), mock(PasswordEncoder.class),
                mock(ThemeInstallService.class), mock(ThemePackageService.class),
                mock(MarketplaceInstallService.class), mock(ThemeSeedOrchestrator.class),
                mock(ArticleI18nService.class), portProvider, seederProvider, new FreeMarkerProperties(),
                mock(InstallTokenFile.class));
    }

    private static Props.Install.AiProvider entry(String name, String baseUrl, String apiKey, String model) {
        Props.Install.AiProvider entry = new Props.Install.AiProvider();
        entry.setName(name);
        entry.setBaseUrl(baseUrl);
        entry.setApiKey(apiKey);
        entry.setModel(model);
        return entry;
    }

    @Test
    void declaredProviderBecomesTheDefaultAndTurnsAiOn() {
        assertEquals("DeepSeek", service.configureAi(
                entry("DeepSeek", "https://api.deepseek.com/v1/", "sk-test", "deepseek-chat")));

        Config.Ai ai = config.getAi();
        assertTrue(ai.isEnabled());
        assertEquals(InstallService.MANIFEST_AI_PROVIDER_CODE, ai.getDefaultProvider());
        Config.Ai.Provider provider = ai.resolveProvider(null);
        assertNotNull(provider);
        assertEquals("DeepSeek", provider.getName());
        // 末尾斜杠去除，与控制台保存同一规范化
        assertEquals("https://api.deepseek.com/v1", provider.getBaseUrl());
        assertEquals("sk-test", provider.getApiKey());
        assertEquals("deepseek-chat", provider.getModel());
        verify(configService).update(config);
    }

    @Test
    void emptyManifestLeavesTheSettingsAlone() {
        assertNull(service.configureAi(new Props.Install.AiProvider()));
        assertFalse(config.getAi().isEnabled());
        verify(configService, never()).update(any());
    }

    @Test
    void nameDefaultsToTheModelAndTheApiKeyIsOptional() {
        assertEquals("qwen3", service.configureAi(entry("", "http://10.0.0.8:11434/v1", "", "qwen3")));

        Config.Ai.Provider provider = config.getAi().resolveProvider(null);
        assertNotNull(provider);
        assertEquals("qwen3", provider.getName());
        assertEquals("", provider.getApiKey());
    }

    @Test
    void resumedInstallUpdatesTheSameProviderAndKeepsTheOthers() {
        Config.Ai stored = new Config.Ai();
        Config.Ai.Provider other = new Config.Ai.Provider();
        other.setCode("p-0a1b2c3d");
        other.setName("GLM");
        other.setBaseUrl("https://open.bigmodel.cn/api/paas/v4");
        other.setModel("glm-4-flash");
        stored.getProviders().add(other);
        stored.setModerationProvider("p-0a1b2c3d");
        config.setAi(stored);

        service.configureAi(entry("DeepSeek", "https://api.deepseek.com/v1", "sk-old", "deepseek-chat"));
        service.configureAi(entry("DeepSeek", "https://api.deepseek.com/v1", "sk-new", "deepseek-reasoner"));

        Config.Ai ai = config.getAi();
        assertEquals(List.of("p-0a1b2c3d", InstallService.MANIFEST_AI_PROVIDER_CODE),
                ai.getProviders().stream().map(Config.Ai.Provider::getCode).toList());
        assertEquals("p-0a1b2c3d", ai.getModerationProvider());
        Config.Ai.Provider provider = ai.resolveProvider(null);
        assertNotNull(provider);
        assertEquals("sk-new", provider.getApiKey());
        assertEquals("deepseek-reasoner", provider.getModel());
        verify(configService, times(2)).update(config);
    }

    @Test
    void baseUrlWithoutModelFailsLoud() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.configureAi(entry("DeepSeek", "https://api.deepseek.com/v1", "sk-test", "")));
        assertTrue(e.getMessage().contains("ujcms.install.ai.provider"), e.getMessage());
        verify(configService, never()).update(any());
    }

    @Test
    void anApiKeyAloneFailsLoud() {
        assertThrows(IllegalArgumentException.class, () -> service.configureAi(entry("", "", "sk-test", "")));
        verify(configService, never()).update(any());
    }

    @Test
    void extraParamsAreStoredAndMustBeAJsonObject() {
        Props.Install.AiProvider manifest = entry("GLM", "https://open.bigmodel.cn/api/paas/v4", "key", "glm-5-flash");
        manifest.setExtraParams("{\"thinking\":{\"type\":\"disabled\"}}");
        service.configureAi(manifest);
        Config.Ai.Provider provider = config.getAi().resolveProvider(null);
        assertNotNull(provider);
        assertEquals("{\"thinking\":{\"type\":\"disabled\"}}", provider.getExtraParams());

        manifest.setExtraParams("thinking=off");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.configureAi(manifest));
        assertTrue(e.getMessage().contains("ujcms.install.ai.provider.extra-params"), e.getMessage());
        verify(configService, times(1)).update(config);
    }

    private static Props.Install.AiMt baiduMt(String appId, String secret) {
        Props.Install.AiMt mt = new Props.Install.AiMt();
        mt.getBaidu().setAppId(appId);
        mt.getBaidu().setSecret(secret);
        return mt;
    }

    @Test
    void baiduTranslateBecomesTheTranslationProviderAndTurnsAiOn() {
        assertEquals(Config.Ai.MT_BAIDU, service.configureAiMt(baiduMt(" 20260101000000001 ", "baidu-secret")));

        Config.Ai ai = config.getAi();
        assertTrue(ai.isEnabled());
        assertEquals(Config.Ai.MT_BAIDU, ai.getTranslationProvider());
        assertEquals("20260101000000001", ai.getMt().getBaiduAppId());
        assertEquals("baidu-secret", ai.getMt().getBaiduSecret());
        assertTrue(ai.resolvesTranslationProvider());
        verify(configService).update(config);
    }

    @Test
    void baiduTranslateKeepsTheManifestProviderAndTheOtherEngines() {
        Config.Ai stored = new Config.Ai();
        stored.getMt().setDeeplAuthKey("deepl-key:fx");
        config.setAi(stored);

        service.configureAi(entry("GLM", "https://open.bigmodel.cn/api/coding/paas/v4", "key", "glm-5.3-flash"));
        service.configureAiMt(baiduMt("20260101000000001", "baidu-secret"));

        Config.Ai ai = config.getAi();
        assertEquals(InstallService.MANIFEST_AI_PROVIDER_CODE, ai.getDefaultProvider());
        assertEquals(Config.Ai.MT_BAIDU, ai.getTranslationProvider());
        assertEquals("deepl-key:fx", ai.getMt().getDeeplAuthKey());
    }

    @Test
    void emptyMtManifestLeavesTheSettingsAlone() {
        assertNull(service.configureAiMt(new Props.Install.AiMt()));
        assertFalse(config.getAi().isEnabled());
        verify(configService, never()).update(any());
    }

    @Test
    void halfABaiduCredentialPairFailsLoud() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.configureAiMt(baiduMt("20260101000000001", "")));
        assertTrue(e.getMessage().contains("ujcms.install.ai.mt.baidu"), e.getMessage());
        verify(configService, never()).update(any());
    }
}
