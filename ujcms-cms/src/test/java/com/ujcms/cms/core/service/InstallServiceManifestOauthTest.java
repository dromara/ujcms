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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 安装清单的第三方登录与注册开关步骤（{@code ujcms.install.oauth} / {@code ujcms.install.register}）：
 * {@link InstallService#configureOauth} / {@link InstallService#configureRegister}。
 */
class InstallServiceManifestOauthTest {
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

    private static Props.Install.OauthClient entry(String clientId, String clientSecret) {
        Props.Install.OauthClient entry = new Props.Install.OauthClient();
        entry.setClientId(clientId);
        entry.setClientSecret(clientSecret);
        return entry;
    }

    @Test
    void declaredProvidersAreStoredAndEnabled() {
        Props.Install.Oauth manifest = new Props.Install.Oauth();
        manifest.setWeixin(entry("wx-app-id", "wx-secret"));
        manifest.setWeibo(entry("wb-key", "wb-secret"));

        assertEquals(List.of("weixin", "weibo"), service.configureOauth(manifest));

        assertEquals(List.of("weixin", "weibo"), config.getOauthProviders());
        assertEquals("wx-secret", config.getOauth().getWeixin().getClientSecret());
        assertFalse(config.getOauth().getQq().isEnabled());
        verify(configService).update(config);
    }

    @Test
    void emptyManifestLeavesTheSettingsAlone() {
        assertTrue(service.configureOauth(new Props.Install.Oauth()).isEmpty());
        verify(configService, never()).update(any());
    }

    @Test
    void undeclaredProvidersKeepTheirCurrentSettings() {
        Config.Oauth stored = new Config.Oauth();
        stored.getQq().setEnabled(true);
        stored.getQq().setClientId("qq-id");
        stored.getQq().setClientSecret("qq-key");
        config.setOauth(stored);
        Props.Install.Oauth manifest = new Props.Install.Oauth();
        manifest.setWeixin(entry("wx-app-id", "wx-secret"));

        service.configureOauth(manifest);

        assertEquals(List.of("weixin", "qq"), config.getOauthProviders());
    }

    @Test
    void halfACredentialPairFailsLoud() {
        Props.Install.Oauth manifest = new Props.Install.Oauth();
        manifest.setQq(entry("qq-id", ""));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.configureOauth(manifest));
        assertTrue(e.getMessage().contains("ujcms.install.oauth.qq"), e.getMessage());
        verify(configService, never()).update(any());
    }

    @Test
    void registerSwitchIsWrittenOnlyWhenDeclared() {
        assertFalse(service.configureRegister(null));
        verify(configService, never()).update(any());

        assertTrue(service.configureRegister(true));
        assertTrue(config.getRegister().isEnabled());
        verify(configService).update(config);
    }
}
