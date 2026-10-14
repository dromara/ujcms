package com.ujcms.cms.core.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.security.TwoFactorGuard;
import com.ujcms.cms.core.support.SecurityProperties;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.service.InstallService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.support.Props;

/**
 * Verifies the public projection produced by {@code EnvController#config()}.
 * The endpoint is anonymous and now returns an <strong>allowlist</strong>
 * {@link EnvController.ConfigResult} record: only fields declared on the record
 * can ever reach an anonymous caller, so a future field added to {@link Config}
 * is private-by-default. Admin-only operational fields (rate-limit thresholds,
 * password aging, SSRF allowlist, upload blacklists) and credentials are never
 * declared here; admins reload the operational subset from
 * {@code /backend/core/config} after authentication.
 *
 * <p>The test serialises the returned record through an {@link ObjectMapper} to
 * the same {@code Map} shape a client receives over the wire, then asserts the
 * contract — without standing up the full {@code @WebMvcTest} harness.
 */
class EnvControllerConfigTest {

    /** Every top-level key the anonymous projection is permitted to emit. */
    private static final Set<String> ALLOWED_TOP_LEVEL_KEYS = Set.of(
            "id", "contextPath", "port", "multiDomain",
            "channelUrl", "articleUrl", "topicUrl", "defaultSiteId",
            "upload", "greyStyle", "register", "security",
            "customs", "commercialTier", "uploadUrlPrefix",
            // 控制台默认外观（侧栏风格、主题色，无机密）：登录页在登录前即按安装默认着色（console-shell D7）
            "console",
            // 可用的第三方登录服务商代码（无凭据；凭据对象 oauth 不在此列）
            "oauthProviders");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EnvController newController(Config config) {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getUnique()).thenReturn(config);
        return new EnvController(mock(SiteService.class), configService, mock(InstallService.class),
                new TwoFactorGuard(new SecurityProperties(), mock(UserService.class)), mock(Props.class));
    }

    private Map<String, Object> configJson(Config config) {
        return objectMapper.convertValue(newController(config).config(),
                new TypeReference<Map<String, Object>>() {});
    }

    private Config configWithFullSecurity() {
        Config config = new Config();
        Config.Security security = new Config.Security();
        security.setPasswordMinLength(8);
        security.setPasswordMaxLength(64);
        security.setPasswordStrength(2);
        security.setUserMaxAttempts(5);
        security.setUserLockMinutes(30);
        security.setIpMaxAttempts(10);
        security.setIpCaptchaAttempts(3);
        security.setPasswordMaxDays(90);
        security.setPasswordMinDays(15);
        security.setPasswordWarnDays(7);
        security.setPasswordMaxHistory(5);
        security.setSsrfWhiteList("inews.gtimg.com\nexample.com");
        security.setTwoFactor(true);
        config.setSecurity(security);
        Config.Grey grey = new Config.Grey();
        grey.setEnabled(true);
        grey.setGreyDates("09-08,12-13");
        config.setGrey(grey);
        config.setUploadsExtensionBlacklist("jsp,php,exe");
        config.setFilesExtensionBlacklist("jsp,php,exe");
        return config;
    }

    @Test
    void onlyAllowlistedTopLevelKeysAreEmitted() {
        Map<String, Object> result = configJson(configWithFullSecurity());
        assertTrue(ALLOWED_TOP_LEVEL_KEYS.containsAll(result.keySet()),
                "anonymous /env/config emitted an unexpected key: "
                        + result.keySet().stream().filter(k -> !ALLOWED_TOP_LEVEL_KEYS.contains(k)).toList());
    }

    @Test
    void publicSecurityFieldsAreExposed() {
        Map<String, Object> result = configJson(configWithFullSecurity());

        Object security = result.get("security");
        assertNotNull(security, "security sub-object should be present");
        Map<?, ?> sec = (Map<?, ?>) security;

        assertEquals(8, sec.get("passwordMinLength"));
        assertEquals(64, sec.get("passwordMaxLength"));
        assertEquals(2, sec.get("passwordStrength"));
        assertEquals(true, sec.get("twoFactor"));
        assertNotNull(sec.get("passwordPattern"), "passwordPattern (computed) must reach the client");
    }

    @Test
    void adminOnlySecurityFieldsAreStripped() {
        Map<String, Object> result = configJson(configWithFullSecurity());

        Map<?, ?> sec = (Map<?, ?>) result.get("security");
        for (String forbidden : new String[]{
                "userMaxAttempts", "userLockMinutes",
                "ipMaxAttempts", "ipCaptchaAttempts",
                "passwordMaxDays", "passwordMinDays", "passwordWarnDays", "passwordMaxHistory",
                "ssrfWhiteList", "ssrfList"}) {
            assertFalse(sec.containsKey(forbidden),
                    "anonymous /env/config must not expose security." + forbidden);
        }
    }

    @Test
    void rawGreyObjectIsStrippedButGreyStyleSurvives() {
        Map<String, Object> result = configJson(configWithFullSecurity());

        assertFalse(result.containsKey("grey"),
                "raw grey object (enabled + greyDates) must not reach anonymous callers");
        assertTrue(result.containsKey("greyStyle"),
                "computed greyStyle boolean is the public-facing display flag");
    }

    @Test
    void uploadExtensionBlacklistsAreNotExposed() {
        Map<String, Object> result = configJson(configWithFullSecurity());

        assertFalse(result.containsKey("uploadsExtensionBlacklist"),
                "upload blacklist aids upload-bypass crafting and is consumed by no client");
        assertFalse(result.containsKey("filesExtensionBlacklist"),
                "files blacklist aids upload-bypass crafting and is consumed by no client");
    }

    @Test
    void noTopLevelCredentialKeysLeak() {
        Map<String, Object> result = configJson(configWithFullSecurity());

        for (String forbidden : new String[]{"sms", "email", "uploadStorage",
                "htmlStorage", "templateStorage",
                "smsSettings", "emailSettings", "securitySettings",
                "uploadStorageSettings", "htmlStorageSettings", "templateStorageSettings"}) {
            assertFalse(result.containsKey(forbidden),
                    "anonymous /env/config must not expose " + forbidden);
        }
    }
}
