package com.ujcms.cms.core.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ujcms.cms.core.marketplace.MarketplaceClient.DownloadGrant;
import com.ujcms.cms.core.marketplace.MarketplaceClient.DownloadedPackage;
import com.ujcms.cms.core.marketplace.MarketplaceClient.UpdateCheckEntry;
import com.ujcms.cms.core.support.Props;

/**
 * {@link MarketplaceClient} 行为回归：契约形状解析、{@code X-UJCMS-Version} 头、短 TTL 缓存、
 * 不可用映射（离线/5xx/429+Retry-After，D-C1-3）、下载授权错误映射与相对引用防护（D-M3-3 只解析
 * 不构造）、下载散列（边写边算，D-K7）。上游以 MockRestServiceServer 模拟。
 */
class MarketplaceClientTest {
    private static final String BASE = "https://store.test";

    private MockRestServiceServer server;
    private MarketplaceClient client;

    @BeforeEach
    void setUp() {
        Props props = mock(Props.class);
        when(props.getVersion()).thenReturn("11.0.2");
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        client = new MarketplaceClient(restClient, restClient, props, true, BASE, 60);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    void listItemsParsesPageSendsVersionHeaderAndCaches() {
        server.expect(once(), requestTo(BASE + "/api/v1/catalog/items?page=1&pageSize=20"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(MarketplaceClient.HEADER_UJCMS_VERSION, "11.0.2"))
                .andRespond(withSuccess("""
                        {"page":1,"pageSize":20,"total":1,"totalPages":1,"items":[
                          {"slug":"corpus-theme","type":"theme","kind":"full","name":"Corpus","priceCents":0,
                           "previewUrl":"/files/items/corpus-theme/preview.png",
                           "latestVersion":{"version":"1.2.3","minUjcmsVersion":"10.0.0"},
                           "futureField":"ignored"}]}
                        """, MediaType.APPLICATION_JSON));

        var page = client.listItems(null, null, null, null, 1, 20);
        assertEquals(1, page.total());
        assertEquals("corpus-theme", page.items().get(0).slug());
        assertEquals("1.2.3", page.items().get(0).latestVersion().version());

        // 第二次命中短 TTL 缓存——once() 之外的再次上游请求会让 verify 失败
        var cachedPage = client.listItems(null, null, null, null, 1, 20);
        assertEquals(1, cachedPage.total());
        server.verify();
    }

    @Test
    void connectFailureMapsToOffline() {
        server.expect(requestTo(BASE + "/api/v1/catalog/items?page=1&pageSize=20"))
                .andRespond(withException(new ConnectException("connection refused")));
        MarketplaceUnavailableException e = assertThrows(MarketplaceUnavailableException.class,
                () -> client.listItems(null, null, null, null, 1, 20));
        assertEquals(MarketplaceUnavailableException.Reason.OFFLINE, e.getReason());
    }

    @Test
    void serverErrorMapsToOffline() {
        server.expect(requestTo(BASE + "/api/v1/catalog/items/corpus-theme"))
                .andRespond(withServerError());
        MarketplaceUnavailableException e = assertThrows(MarketplaceUnavailableException.class,
                () -> client.getItem("corpus-theme"));
        assertEquals(MarketplaceUnavailableException.Reason.OFFLINE, e.getReason());
    }

    @Test
    void rateLimitMapsToRateLimitedWithRetryAfter() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "7");
        server.expect(requestTo(BASE + "/api/v1/catalog/items?page=1&pageSize=20"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"status\":429,\"code\":\"rate_limited\"}"));
        MarketplaceUnavailableException e = assertThrows(MarketplaceUnavailableException.class,
                () -> client.listItems(null, null, null, null, 1, 20));
        assertEquals(MarketplaceUnavailableException.Reason.RATE_LIMITED, e.getReason());
        assertEquals(7, e.getRetryAfterSeconds());
    }

    @Test
    void updateCheckPostsEntriesAndParsesResults() {
        server.expect(requestTo(BASE + "/api/v1/catalog/update-check"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[{\"name\":\"corpus-theme\",\"version\":\"1.0.0\"}]"))
                .andRespond(withSuccess("""
                        [{"name":"corpus-theme","installedVersion":"1.0.0","updateAvailable":true,
                          "latestVersion":{"version":"1.2.3"}}]
                        """, MediaType.APPLICATION_JSON));
        var results = client.updateCheck(List.of(new UpdateCheckEntry("corpus-theme", "1.0.0")));
        assertEquals(1, results.size());
        assertTrue(results.get(0).updateAvailable());
        assertEquals("1.2.3", results.get(0).latestVersion().version());
    }

    @Test
    void downloadGrant404MapsToItemNotFound() {
        server.expect(requestTo(BASE + "/api/v1/downloads"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"status\":404,\"code\":\"not_found\"}"));
        MarketplaceException e = assertThrows(MarketplaceException.class,
                () -> client.requestDownload("nope", null));
        assertEquals("marketplace.error.itemNotFound", e.getMessage());
    }

    @Test
    void downloadGrant403MapsToNotEntitled() {
        server.expect(requestTo(BASE + "/api/v1/downloads"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"status\":403,\"code\":\"not_entitled\"}"));
        MarketplaceException e = assertThrows(MarketplaceException.class,
                () -> client.requestDownload("enterprise-portal", null));
        assertEquals("marketplace.error.notEntitled", e.getMessage());
    }

    @Test
    void downloadPackageStreamsToTempAndHashes() throws Exception {
        byte[] packageBytes = "fake-zip-bytes".getBytes(StandardCharsets.UTF_8);
        String expectedSha = sha256(packageBytes);
        server.expect(requestTo(BASE + "/files/packages/corpus-theme/corpus-theme-1.2.3.zip?expires=1&sig=ab"))
                .andExpect(header(MarketplaceClient.HEADER_UJCMS_VERSION, "11.0.2"))
                .andRespond(withSuccess(packageBytes, MediaType.APPLICATION_OCTET_STREAM));

        DownloadGrant grant = new DownloadGrant(
                "/files/packages/corpus-theme/corpus-theme-1.2.3.zip?expires=1&sig=ab",
                expectedSha, packageBytes.length, null);
        DownloadedPackage pkg = client.downloadPackage(grant);
        try {
            assertEquals(expectedSha, pkg.sha256());
            assertEquals(packageBytes.length, pkg.size());
            assertNotNull(pkg.file());
            assertEquals(packageBytes.length, Files.size(pkg.file()));
        } finally {
            Files.deleteIfExists(pkg.file());
        }
    }

    @Test
    void downloadPackageRejectsAbsoluteOrNonFileUrls() {
        MarketplaceException e = assertThrows(MarketplaceException.class, () -> client.downloadPackage(
                new DownloadGrant("https://evil.example/x.zip", "ab", 1, null)));
        assertEquals("marketplace.error.downloadFailed", e.getMessage());
    }

    @Test
    void downloadPackageNon2xxIsActionable() {
        // 占位存储键（M1 种子条目）：授权成功但取包 404——须给出可诉错误而非未处理异常
        server.expect(requestTo(BASE + "/files/packages/ghost/ghost-1.0.0.zip?expires=1&sig=ab"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        MarketplaceException e = assertThrows(MarketplaceException.class, () -> client.downloadPackage(
                new DownloadGrant("/files/packages/ghost/ghost-1.0.0.zip?expires=1&sig=ab", "ab", 1, null)));
        assertEquals("marketplace.error.downloadFailed", e.getMessage());
    }

    @Test
    void fetchAssetRejectsIllegalPaths() {
        // 包路径不许走资产代理（签名授权是唯一取包通道，D-M3-3）；上跳与绝对 URL 一律拒绝
        for (String path : new String[]{"/files/packages/x.zip", "/files/items/../secret",
                "https://evil.example/a.png", "/other/prefix.png"}) {
            MarketplaceException e = assertThrows(MarketplaceException.class, () -> client.fetchAsset(path), path);
            assertEquals("marketplace.error.assetNotFound", e.getMessage(), path);
        }
    }

    @Test
    void fetchAssetReturnsBytesAndHeaders() {
        server.expect(requestTo(BASE + "/files/items/corpus-theme/preview.png"))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.IMAGE_PNG));
        var asset = client.fetchAsset("/files/items/corpus-theme/preview.png");
        assertEquals(3, asset.bytes().length);
        assertEquals(MediaType.IMAGE_PNG_VALUE, asset.contentType());
    }

    // ----- oauth / account（C2） -----

    @Test
    void requestDeviceCodeSendsFormAndParsesSnakeCase() {
        server.expect(once(), requestTo(BASE + "/api/v1/oauth/device/code"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(header(MarketplaceClient.HEADER_UJCMS_VERSION, "11.0.2"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=ujcms-instance")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "scope=catalog%3Aread+entitlements%3Aread+download")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("instance_name=My+CMS")))
                .andRespond(withSuccess("""
                        {"device_code":"dev-1","user_code":"ABCD-EFGH",
                         "verification_uri":"http://store.test/device",
                         "verification_uri_complete":"http://store.test/device?user_code=ABCD-EFGH",
                         "expires_in":600,"interval":5}
                        """, MediaType.APPLICATION_JSON));

        var authorization = client.requestDeviceCode("My CMS", null);
        assertEquals("dev-1", authorization.deviceCode());
        assertEquals("ABCD-EFGH", authorization.userCode());
        assertEquals(5, authorization.interval());
        server.verify();
    }

    @Test
    void pollDeviceTokenSurfacesRfcErrorCodes() {
        for (String error : new String[]{"authorization_pending", "slow_down", "access_denied", "expired_token"}) {
            server.reset();
            server.expect(once(), requestTo(BASE + "/api/v1/oauth/token"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":\"" + error + "\"}"));
            MarketplaceOAuthException e = assertThrows(MarketplaceOAuthException.class,
                    () -> client.pollDeviceToken("dev-1"), error);
            assertEquals(error, e.getError());
        }
        server.verify();
    }

    @Test
    void pollDeviceTokenParsesIssuedTokens() {
        server.expect(once(), requestTo(BASE + "/api/v1/oauth/token"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("device_code=dev-1")))
                .andRespond(withSuccess("""
                        {"access_token":"at-1","token_type":"Bearer","expires_in":900,
                         "refresh_token":"rt-1","scope":"catalog:read entitlements:read download"}
                        """, MediaType.APPLICATION_JSON));
        var tokens = client.pollDeviceToken("dev-1");
        assertEquals("at-1", tokens.accessToken());
        assertEquals("rt-1", tokens.refreshToken());
        assertEquals(900, tokens.expiresIn());
    }

    @Test
    void refreshSendsRefreshGrantAndParsesRotatedPair() {
        server.expect(once(), requestTo(BASE + "/api/v1/oauth/token"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=refresh_token")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("refresh_token=rt-1")))
                .andRespond(withSuccess("""
                        {"access_token":"at-2","token_type":"Bearer","expires_in":900,"refresh_token":"rt-2"}
                        """, MediaType.APPLICATION_JSON));
        var tokens = client.refreshAccessToken("rt-1");
        assertEquals("at-2", tokens.accessToken());
        assertEquals("rt-2", tokens.refreshToken());
    }

    @Test
    void refreshInvalidGrantSurfacesOAuthError() {
        server.expect(once(), requestTo(BASE + "/api/v1/oauth/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\",\"error_description\":\"revoked\"}"));
        MarketplaceOAuthException e = assertThrows(MarketplaceOAuthException.class,
                () -> client.refreshAccessToken("rt-dead"));
        assertTrue(e.is(MarketplaceOAuthException.INVALID_GRANT));
    }

    @Test
    void entitlementsSendBearerAndParsePage() {
        server.expect(once(), requestTo(BASE + "/api/v1/me/entitlements?page=1&pageSize=100"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer at-1"))
                .andRespond(withSuccess("""
                        {"page":1,"pageSize":100,"total":1,"totalPages":1,
                         "items":[{"item":"edu-campus","name":"智慧校园","source":"grant",
                                   "grantedAt":"2026-07-18T10:00:00Z"}]}
                        """, MediaType.APPLICATION_JSON));
        var page = client.listEntitlements("at-1", 1, 100);
        assertEquals(1, page.total());
        assertEquals("edu-campus", page.items().get(0).item());
    }

    @Test
    void bearerCallsMapAuthFailures() {
        // 401：令牌无效/过期（市场重启换密钥）→ UNAUTHORIZED（调用方刷新重试）
        server.expect(once(), requestTo(BASE + "/api/v1/me/entitlements?page=1&pageSize=100"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        MarketplaceAuthException unauthorized = assertThrows(MarketplaceAuthException.class,
                () -> client.listEntitlements("at-dead", 1, 100));
        assertEquals(MarketplaceAuthException.Kind.UNAUTHORIZED, unauthorized.getKind());

        // 403 problem+json code=insufficient_scope → 须重连（scope 集合变更，刷新救不回来）
        server.reset();
        server.expect(once(), requestTo(BASE + "/api/v1/me/entitlements?page=1&pageSize=100"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"code\":\"insufficient_scope\"}"));
        MarketplaceAuthException scope = assertThrows(MarketplaceAuthException.class,
                () -> client.listEntitlements("at-scopeless", 1, 100));
        assertEquals(MarketplaceAuthException.Kind.INSUFFICIENT_SCOPE, scope.getKind());
    }

    @Test
    void downloadGrantCarriesBearerWhenTokenPresent() {
        server.expect(once(), requestTo(BASE + "/api/v1/downloads"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer at-1"))
                .andRespond(withSuccess("""
                        {"url":"/files/packages/edu-campus/edu-campus-2.0.0.zip?expires=1&sig=ab",
                         "sha256":"%s","size":10}
                        """.formatted("ab".repeat(32)), MediaType.APPLICATION_JSON));
        var grant = client.requestDownload("edu-campus", "2.0.0", "at-1");
        assertEquals(10, grant.size());
    }

    @Test
    void downloadGrant403SplitsScopeFromEntitlement() {
        // not_entitled（业务性拒绝）→ 可诉 400；insufficient_scope → AuthException（须重连）
        server.expect(once(), requestTo(BASE + "/api/v1/downloads"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"code\":\"not_entitled\"}"));
        MarketplaceException notEntitled = assertThrows(MarketplaceException.class,
                () -> client.requestDownload("edu-campus", null, "at-1"));
        assertEquals("marketplace.error.notEntitled", notEntitled.getMessage());

        server.reset();
        server.expect(once(), requestTo(BASE + "/api/v1/downloads"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"code\":\"insufficient_scope\"}"));
        MarketplaceAuthException scope = assertThrows(MarketplaceAuthException.class,
                () -> client.requestDownload("edu-campus", null, "at-scopeless"));
        assertEquals(MarketplaceAuthException.Kind.INSUFFICIENT_SCOPE, scope.getKind());
    }

    @Test
    void revokeTokenPostsRevocationForm() {
        server.expect(once(), requestTo(BASE + "/api/v1/oauth/revoke"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("token=rt-1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("token_type_hint=refresh_token")))
                .andRespond(withSuccess());
        client.revokeToken("rt-1", "refresh_token");
        server.verify();
    }

    @Test
    void getMeParsesAccountAndInstance() {
        server.expect(once(), requestTo(BASE + "/api/v1/me"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer at-1"))
                .andRespond(withSuccess("""
                        {"account":{"id":"acc-1","email":"dev@ujcms.com","displayName":"Dev"},
                         "instance":{"id":"inst-1","name":"UJCMS 11.0.2"}}
                        """, MediaType.APPLICATION_JSON));
        var me = client.getMe("at-1");
        assertEquals("dev@ujcms.com", me.account().email());
        assertEquals("inst-1", me.instance().id());
    }
}
