package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ujcms.cms.core.support.Constants;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Config.Oauth}：第三方登录设置的 JSON 往返、可用服务商判定、原始列与缓存的一致性、密钥写时合并，
 * 以及实体 JSON 只带服务商代码、不带凭据。
 */
class ConfigOauthTest {

    private static Config.Oauth.Client client(boolean enabled, String clientId, String clientSecret) {
        Config.Oauth.Client client = new Config.Oauth.Client();
        client.setEnabled(enabled);
        client.setClientId(clientId);
        client.setClientSecret(clientSecret);
        return client;
    }

    @Test
    void blankColumnYieldsNoProviders() {
        Config config = new Config();
        assertNull(config.getOauthSettings());
        assertTrue(config.getOauthProviders().isEmpty());
        assertFalse(config.getOauth().getWeixin().isEnabled());
        // 缺省对象不落列
        assertNull(config.getOauthSettings());
    }

    @Test
    void roundTripThroughTheColumn() {
        Config.Oauth oauth = new Config.Oauth();
        oauth.setWeixin(client(true, "wx-app-id", "wx-secret"));

        Config config = new Config();
        config.setOauth(oauth);
        String json = config.getOauthSettings();

        Config loaded = new Config();
        loaded.setOauthSettings(json);
        assertTrue(loaded.getOauth().getWeixin().isEnabled());
        assertEquals("wx-app-id", loaded.getOauth().getWeixin().getClientId());
        assertEquals("wx-secret", loaded.getOauth().getWeixin().getClientSecret());
        assertFalse(loaded.getOauth().getQq().isEnabled());
    }

    @Test
    void onlyEnabledProvidersWithFullCredentialsAreUsable() {
        Config.Oauth oauth = new Config.Oauth();
        oauth.setWeibo(client(true, "wb-key", "wb-secret"));
        // 已启用但缺密钥：不可用
        oauth.setQq(client(true, "qq-id", ""));
        // 凭据齐全但未启用：不可用
        oauth.setWeixin(client(false, "wx-app-id", "wx-secret"));
        assertEquals(List.of("weibo"), oauth.usableProviders());

        oauth.getWeixin().setEnabled(true);
        // 顺序恒为 Config.Oauth.PROVIDERS 的顺序，与设置先后无关
        assertEquals(List.of("weixin", "weibo"), oauth.usableProviders());
        assertNull(oauth.client("github"));
    }

    @Test
    void settingTheRawColumnDropsTheCachedObject() {
        Config config = new Config();
        assertTrue(config.getOauthProviders().isEmpty());
        config.setOauthSettings("{\"qq\":{\"enabled\":true,\"clientId\":\"qq-id\",\"clientSecret\":\"qq-key\"}}");
        assertEquals(List.of("qq"), config.getOauthProviders());
    }

    @Test
    void blankSecretsKeepTheStoredOnes() {
        Config.Oauth stored = new Config.Oauth();
        stored.setWeixin(client(true, "wx-app-id", "wx-secret"));
        stored.setQq(client(true, "qq-id", "qq-key"));

        Config.Oauth submitted = new Config.Oauth();
        // 留空：沿用已存密钥
        submitted.setWeixin(client(true, "wx-app-id-2", ""));
        // 重新录入：以新值为准
        submitted.setQq(client(true, "qq-id", "qq-key-2"));
        stored.fillStoredSecrets(submitted);

        assertEquals("wx-secret", submitted.getWeixin().getClientSecret());
        assertEquals("wx-app-id-2", submitted.getWeixin().getClientId());
        assertEquals("qq-key-2", submitted.getQq().getClientSecret());
        assertEquals("", submitted.getWeibo().getClientSecret());
    }

    @Test
    void credentialsAreTrimmedAndNullSafe() throws JsonProcessingException {
        Config.Oauth read = Constants.MAPPER.readValue(
                "{\"weixin\":{\"enabled\":true,\"clientId\":\" wx-app-id \",\"clientSecret\":null},\"qq\":null,\"future\":1}",
                Config.Oauth.class);
        assertEquals("wx-app-id", read.getWeixin().getClientId());
        assertEquals("", read.getWeixin().getClientSecret());
        assertFalse(read.getQq().isEnabled());
        assertTrue(read.usableProviders().isEmpty());
    }

    @Test
    void entityJsonCarriesProviderCodesButNoCredentials() throws JsonProcessingException {
        // GET /backend/core/config 返回整个实体：只带可用服务商代码；凭据对象与原始列都不序列化
        Config.Oauth oauth = new Config.Oauth();
        oauth.setWeixin(client(true, "wx-app-id", "wx-secret"));
        Config config = new Config();
        config.setOauth(oauth);
        String text = Constants.MAPPER.writeValueAsString(config);
        var json = Constants.MAPPER.readTree(text);
        assertEquals("weixin", json.get("oauthProviders").get(0).asText());
        assertFalse(json.has("oauth"), text);
        assertFalse(json.has("oauthSettings"), text);
        assertFalse(text.contains("wx-secret"), text);
    }
}
