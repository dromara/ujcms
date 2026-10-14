package com.ujcms.cms.core.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ujcms.cms.core.marketplace.MarketplaceClient.CatalogItemDetail;
import com.ujcms.cms.core.marketplace.MarketplaceClient.CatalogItemPage;
import com.ujcms.cms.core.marketplace.MarketplaceClient.DownloadGrant;
import com.ujcms.cms.core.marketplace.MarketplaceClient.EntitlementPage;
import com.ujcms.cms.core.marketplace.MarketplaceClient.ItemVersion;
import com.ujcms.cms.core.marketplace.MarketplaceClient.Me;
import com.ujcms.cms.core.marketplace.MarketplaceClient.UpdateCheckEntry;
import com.ujcms.cms.core.marketplace.MarketplaceClient.UpdateCheckResult;
import com.ujcms.cms.core.support.Props;

/**
 * {@link MarketplaceClient} 契约测试——录制回放（C3）：夹具是 2026-07-18 对 dev 市场
 * （契约 1.0.0）逐端点录下的真实响应字节（{@code src/test/resources/marketplace/recorded/}，
 * 录制说明见其 README），经 MockRestServiceServer 回放，断言客户端对「实际线上形状」
 * 的解析与错误映射——DTO 相对已服务的线格式漂移即断（与 {@link MarketplaceClientTest}
 * 的手写 JSON 行为回归互补）。
 */
class MarketplaceClientRecordedContractTest {
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

    private static String recorded(String name) {
        String path = "/marketplace/recorded/" + name;
        try (InputStream in = MarketplaceClientRecordedContractTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "recorded fixture missing: " + path);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ----- catalog ----------------------------------------------------------

    @Test
    void itemsPageRecorded() {
        server.expect(requestTo(BASE + "/api/v1/catalog/items?page=1&pageSize=100"))
                .andRespond(withSuccess(recorded("catalog-items-page.json"), MediaType.APPLICATION_JSON));
        CatalogItemPage page = client.listItems(null, null, null, null, 1, 100);
        assertEquals(6, page.total());
        assertEquals(6, page.items().size());
        var first = page.items().get(0);
        assertEquals("corpus-theme", first.slug());
        assertEquals("theme", first.type());
        assertEquals("full", first.kind());
        assertEquals(0, first.priceCents());
        assertEquals("/files/items/corpus-theme/preview.png", first.previewUrl());
        assertEquals("1.2.3", first.latestVersion().version());
        assertEquals("10.0.0", first.latestVersion().minUjcmsVersion());
        var paid = page.items().stream().filter(i -> "edu-campus".equals(i.slug())).findFirst().orElseThrow();
        assertEquals(29900, paid.priceCents());
    }

    @Test
    void itemDetailRecorded() {
        server.expect(requestTo(BASE + "/api/v1/catalog/items/edu-campus"))
                .andRespond(withSuccess(recorded("catalog-item-detail.json"), MediaType.APPLICATION_JSON));
        CatalogItemDetail detail = client.getItem("edu-campus");
        assertEquals("智慧校园", detail.name());
        assertNotNull(detail.description());
        assertEquals(2, detail.screenshots().size());
        assertEquals("/files/items/edu-campus/screenshots/1.png", detail.screenshots().get(0).url());
        assertEquals(3, detail.versions().size());
        assertEquals("2.1.0", detail.latestVersion().version());
    }

    @Test
    void itemVersionsRecorded() {
        server.expect(requestTo(BASE + "/api/v1/catalog/items/edu-campus/versions"))
                .andRespond(withSuccess(recorded("catalog-item-versions.json"), MediaType.APPLICATION_JSON));
        List<ItemVersion> versions = client.listVersions("edu-campus");
        assertEquals(3, versions.size());
        // 语义化版本新在前；sha256/size 契约必载（D-K7）
        assertEquals("2.1.0", versions.get(0).version());
        assertEquals(64, versions.get(0).sha256().length());
        assertTrue(versions.get(0).size() > 0);
        assertEquals("- 初始版本", versions.get(2).changelog());
    }

    @Test
    void updateCheckRecorded() {
        server.expect(requestTo(BASE + "/api/v1/catalog/update-check"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(recorded("catalog-update-check.json"), MediaType.APPLICATION_JSON));
        List<UpdateCheckResult> results = client.updateCheck(List.of(
                new UpdateCheckEntry("edu-campus", "2.0.0"),
                new UpdateCheckEntry("corpus-theme", "1.2.3"),
                new UpdateCheckEntry("my-renamed-theme", "1.0.0")));
        assertEquals(3, results.size());
        assertTrue(results.get(0).updateAvailable());
        assertEquals("2.1.0", results.get(0).latestVersion().version());
        // 已是最新 / 目录不识别的重命名安装：逐条降级为无更新（D-C2-5）
        assertFalse(results.get(1).updateAvailable());
        assertFalse(results.get(2).updateAvailable());
    }

    @Test
    void itemNotFoundProblemRecorded() {
        server.expect(requestTo(BASE + "/api/v1/downloads"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(recorded("problem-item-not-found.json")));
        MarketplaceException e = assertThrows(MarketplaceException.class,
                () -> client.requestDownload("no-such-item", null));
        assertEquals("marketplace.error.itemNotFound", e.getMessage());
    }

    @Test
    void rateLimitedProblemRecorded() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "1"); // 录制响应携带 Retry-After: 1
        server.expect(requestTo(BASE + "/api/v1/catalog/items?page=1&pageSize=20"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(recorded("problem-rate-limited.json")));
        MarketplaceUnavailableException e = assertThrows(MarketplaceUnavailableException.class,
                () -> client.listItems(null, null, null, null, 1, 20));
        assertEquals(MarketplaceUnavailableException.Reason.RATE_LIMITED, e.getReason());
        assertEquals(1, e.getRetryAfterSeconds());
    }

    // ----- downloads（D-K7 / D-M3-7 签名 URL 的三种失败形状） -----------------

    @Test
    void downloadGrantRecorded() {
        server.expect(requestTo(BASE + "/api/v1/downloads"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(recorded("download-grant-free.json")));
        DownloadGrant grant = client.requestDownload("corpus-theme", "1.2.3");
        assertTrue(grant.url().startsWith("/files/packages/corpus-theme/"));
        assertEquals("fa3751cae17117337489ec772be53f6ba2d8b4f868f5ca297a0bcab5ddfd5296", grant.sha256());
        assertEquals(2378, grant.size());
        assertNotNull(grant.expiresAt());
    }

    @Test
    void notEntitledProblemRecorded() {
        server.expect(requestTo(BASE + "/api/v1/downloads"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(recorded("problem-not-entitled.json")));
        MarketplaceException e = assertThrows(MarketplaceException.class,
                () -> client.requestDownload("edu-campus", null));
        assertEquals("marketplace.error.notEntitled", e.getMessage());
    }

    @Test
    void expiredSignedUrlProblemRecorded() {
        server.expect(requestTo(BASE + "/files/packages/corpus-theme/x.zip?expires=1&sig=ab"))
                .andRespond(withStatus(HttpStatus.GONE)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(recorded("problem-url-expired.json")));
        MarketplaceException e = assertThrows(MarketplaceException.class, () -> client.downloadPackage(
                new DownloadGrant("/files/packages/corpus-theme/x.zip?expires=1&sig=ab", "ab", 1, null)));
        assertEquals("marketplace.error.downloadFailed", e.getMessage());
    }

    @Test
    void tamperedSignatureProblemRecorded() {
        server.expect(requestTo(BASE + "/files/packages/corpus-theme/x.zip?expires=1&sig=ffff"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(recorded("problem-invalid-signature.json")));
        MarketplaceException e = assertThrows(MarketplaceException.class, () -> client.downloadPackage(
                new DownloadGrant("/files/packages/corpus-theme/x.zip?expires=1&sig=ffff", "ab", 1, null)));
        assertEquals("marketplace.error.downloadFailed", e.getMessage());
    }

    // ----- oauth / account ---------------------------------------------------

    @Test
    void deviceCodeRecorded() {
        server.expect(requestTo(BASE + "/api/v1/oauth/device/code"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(recorded("oauth-device-code.json"), MediaType.APPLICATION_JSON));
        var authorization = client.requestDeviceCode("C3 sweep", null);
        assertEquals("CNZC-JNGK", authorization.userCode());
        assertEquals("http://localhost:3000/device", authorization.verificationUri());
        assertTrue(authorization.verificationUriComplete().endsWith("user_code=CNZC-JNGK"));
        assertEquals(599, authorization.expiresIn());
        assertEquals(5, authorization.interval());
    }

    @Test
    void tokenPendingRecorded() {
        server.expect(requestTo(BASE + "/api/v1/oauth/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(recorded("oauth-token-pending.json")));
        MarketplaceOAuthException e = assertThrows(MarketplaceOAuthException.class,
                () -> client.pollDeviceToken("dev-1"));
        assertTrue(e.is(MarketplaceOAuthException.AUTHORIZATION_PENDING));
    }

    @Test
    void tokenSuccessRecorded() {
        server.expect(requestTo(BASE + "/api/v1/oauth/token"))
                .andRespond(withSuccess(recorded("oauth-token-success.json"), MediaType.APPLICATION_JSON));
        var tokens = client.pollDeviceToken("dev-1");
        assertEquals("REDACTED-ACCESS-TOKEN", tokens.accessToken());
        assertEquals("REDACTED-REFRESH-TOKEN", tokens.refreshToken());
        assertEquals(899, tokens.expiresIn());
        // 服务端返回的 scope 串不保序（录制为 "download entitlements:read catalog:read"）
        assertEquals(java.util.Set.of("catalog:read", "entitlements:read", "download"),
                java.util.Set.of(tokens.scope().split(" ")));
    }

    @Test
    void meRecorded() {
        server.expect(requestTo(BASE + "/api/v1/me"))
                .andRespond(withSuccess(recorded("me.json"), MediaType.APPLICATION_JSON));
        Me me = client.getMe("at-1");
        assertEquals("c3-buyer@ujcms.test", me.account().email());
        assertEquals("C3 Sweep Buyer", me.account().displayName());
        assertEquals("C3 Recorder", me.instance().name());
        // M7 起 Instance 的 domain 可为 null（contract 0.6.0 nullable 化）——解析不得炸
        assertEquals("11.0.2", me.instance().ujcmsVersion());
    }

    @Test
    void entitlementsRecorded() {
        server.expect(requestTo(BASE + "/api/v1/me/entitlements?page=1&pageSize=100"))
                .andRespond(withSuccess(recorded("entitlements.json"), MediaType.APPLICATION_JSON));
        EntitlementPage page = client.listEntitlements("at-1", 1, 100);
        assertEquals(1, page.total());
        assertEquals("edu-campus", page.items().get(0).item());
        assertEquals("order", page.items().get(0).source());
    }
}
