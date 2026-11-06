package com.ujcms.cms.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 验证已存密码/密钥的沿用规则：表单留空时只沿用到同一目的地（AI 接口地址、邮件服务器、存储 endpoint / FTP 主机）。
 * 目的地一变就不沿用，否则改一下地址再测试或保存，即可把已存凭据发往任意主机
 */
class ConfigStoredSecretTest {
    private static final String STORED_URL = "https://open.bigmodel.cn/api/paas/v4";

    private static Config.Ai storedAi() {
        Config.Ai.Provider stored = provider("p-1", STORED_URL, "stored-secret");
        Config.Ai ai = new Config.Ai();
        ai.getProviders().add(stored);
        return ai;
    }

    private static Config.Ai.Provider provider(String code, String baseUrl, String apiKey) {
        Config.Ai.Provider provider = new Config.Ai.Provider();
        provider.setCode(code);
        provider.setName(code);
        provider.setBaseUrl(baseUrl);
        provider.setApiKey(apiKey);
        provider.setModel("glm-5.3");
        return provider;
    }

    @Test
    void blankKeyBorrowsStoredKeyOfSameCodeAndBaseUrl() {
        Config.Ai.Provider submitted = provider("p-1", STORED_URL + "/", "");
        assertFalse(storedAi().fillStoredApiKey(submitted));
        assertEquals("stored-secret", submitted.getApiKey(), "末尾斜杠不算地址变更");
    }

    @Test
    void changedBaseUrlWithholdsStoredKey() {
        Config.Ai.Provider submitted = provider("p-1", "https://attacker.example/v1", "");
        assertTrue(storedAi().fillStoredApiKey(submitted), "地址变更须报告给调用方");
        assertEquals("", submitted.getApiKey(), "已存密钥不得发往新地址");

        Config.Ai.Provider otherPathSameHost = provider("p-1", "https://open.bigmodel.cn/api/coding/paas/v4", "");
        assertTrue(storedAi().fillStoredApiKey(otherPathSameHost), "同主机换路径也算变更");
        assertEquals("", otherPathSameHost.getApiKey());
    }

    @Test
    void enteredKeyOrUnknownCodeIsLeftAlone() {
        Config.Ai.Provider entered = provider("p-1", "https://other.example/v1", "new-secret");
        assertFalse(storedAi().fillStoredApiKey(entered));
        assertEquals("new-secret", entered.getApiKey());

        Config.Ai.Provider unsaved = provider("p-2", "https://other.example/v1", "");
        assertFalse(storedAi().fillStoredApiKey(unsaved), "新增的服务商没有已存密钥，无所谓沿用");
        assertEquals("", unsaved.getApiKey());
    }

    @Test
    void storedProviderWithoutKeyIsNotReportedAsWithheld() {
        Config.Ai ai = new Config.Ai();
        ai.getProviders().add(provider("p-1", "http://127.0.0.1:11434/v1", ""));
        Config.Ai.Provider moved = provider("p-1", "http://10.0.0.8:11434/v1", "");
        assertFalse(ai.fillStoredApiKey(moved), "原本就没有密钥（Ollama），换地址不需要重新填写");
    }

    private static Config.Email email(String host, String password) {
        Config.Email email = new Config.Email();
        email.setHost(host);
        email.setPassword(password);
        return email;
    }

    @Test
    void emailPasswordFollowsTheSmtpHost() {
        Config.Email stored = email("smtp.example.com", "smtp-secret");

        Config.Email sameHost = email("SMTP.Example.com", "");
        assertFalse(sameHost.fillStoredSecrets(stored));
        assertEquals("smtp-secret", sameHost.getPassword(), "主机名不区分大小写");

        Config.Email movedHost = email("smtp.attacker.example", "");
        assertTrue(movedHost.fillStoredSecrets(stored), "地址变更须报告给调用方");
        assertEquals("", movedHost.getPassword(), "已存密码不得发往新服务器");

        Config.Email entered = email("smtp.other.example", "new-secret");
        assertFalse(entered.fillStoredSecrets(stored));
        assertEquals("new-secret", entered.getPassword(), "重新填写的密码照常使用");
    }

    private static Config.Storage storage(String endpoint, String accessKey, String secretKey, String hostname,
                                          String password) {
        Config.Storage storage = new Config.Storage();
        storage.setEndpoint(endpoint);
        storage.setAccessKey(accessKey);
        storage.setSecretKey(secretKey);
        storage.setHostname(hostname);
        storage.setPassword(password);
        return storage;
    }

    @Test
    void storageKeysFollowTheEndpointAndFtpPasswordFollowsTheHostname() {
        Config.Storage stored = storage("https://minio.example.com", "AK", "SK", "ftp.example.com", "ftp-secret");

        Config.Storage unchanged = storage("https://minio.example.com/", "", "", "ftp.example.com", "");
        unchanged.fillStoredSecrets(stored);
        assertEquals("AK", unchanged.getAccessKey());
        assertEquals("SK", unchanged.getSecretKey());
        assertEquals("ftp-secret", unchanged.getPassword());

        Config.Storage movedEndpoint = storage("https://s3.attacker.example", "", "", "ftp.example.com", "");
        movedEndpoint.fillStoredSecrets(stored);
        assertEquals("", movedEndpoint.getAccessKey(), "对象存储密钥只发往原 endpoint");
        assertEquals("", movedEndpoint.getSecretKey());
        assertEquals("ftp-secret", movedEndpoint.getPassword(), "FTP 主机未变，密码照常沿用");

        Config.Storage movedFtp = storage("https://minio.example.com", "", "", "ftp.attacker.example", "");
        movedFtp.fillStoredSecrets(stored);
        assertEquals("AK", movedFtp.getAccessKey(), "endpoint 未变，对象存储密钥照常沿用");
        assertEquals("", movedFtp.getPassword(), "FTP 密码只发往原主机");
    }
}
