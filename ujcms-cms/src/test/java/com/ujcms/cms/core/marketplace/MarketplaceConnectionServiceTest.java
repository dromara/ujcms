package com.ujcms.cms.core.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ujcms.cms.core.marketplace.MarketplaceClient.DeviceAuthorization;
import com.ujcms.cms.core.marketplace.MarketplaceClient.Entitlement;
import com.ujcms.cms.core.marketplace.MarketplaceClient.EntitlementPage;
import com.ujcms.cms.core.marketplace.MarketplaceClient.Me;
import com.ujcms.cms.core.marketplace.MarketplaceClient.MeAccount;
import com.ujcms.cms.core.marketplace.MarketplaceClient.MeInstance;
import com.ujcms.cms.core.marketplace.MarketplaceClient.OAuthTokens;
import com.ujcms.cms.core.marketplace.MarketplaceConnectionService.ConnectionStatus;
import com.ujcms.cms.core.marketplace.MarketplaceConnectionService.DeviceStart;
import com.ujcms.cms.core.marketplace.MarketplaceConnectionService.EntitlementsSnapshot;
import com.ujcms.cms.core.marketplace.MarketplaceConnectionService.PollResult;
import com.ujcms.cms.core.marketplace.MarketplaceConnectionService.PollStatus;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Props;

/**
 * {@link MarketplaceConnectionService} 行为回归：设备授权流轮询节流（interval / slow_down +5s，
 * RFC 8628 §3.5）、批准即持久化、到期透明刷新与<b>先落库再用</b>的轮换纪律、401 强制刷新重试一次、
 * {@code invalid_grant}/{@code insufficient_scope} 置 reconnectRequired（终局）、断开尽力吊销。
 * 存储以内存假件模拟（load/save/clear 直写 AtomicReference），时钟可控。
 */
class MarketplaceConnectionServiceTest {
    private MarketplaceClient client;
    private MarketplaceConnectionStore store;
    private final AtomicReference<MarketplaceConnection> stored = new AtomicReference<>();
    private final long[] nowMillis = {1_000_000_000_000L};
    private MarketplaceConnectionService service;

    @BeforeEach
    void setUp() {
        client = mock(MarketplaceClient.class);
        store = mock(MarketplaceConnectionStore.class);
        stored.set(null);
        when(store.load()).thenAnswer(inv -> stored.get());
        doAnswer(inv -> {
            stored.set(inv.getArgument(0));
            return null;
        }).when(store).save(any());
        doAnswer(inv -> {
            stored.set(null);
            return null;
        }).when(store).clear();
        Props props = mock(Props.class);
        when(props.getVersion()).thenReturn("11.0.2");
        service = new MarketplaceConnectionService(client, store, props, () -> nowMillis[0]);
    }

    private void advanceSeconds(long seconds) {
        nowMillis[0] += seconds * 1000;
    }

    private long nowSeconds() {
        return nowMillis[0] / 1000;
    }

    private DeviceStart startFlow() {
        when(client.requestDeviceCode(anyString(), any())).thenReturn(new DeviceAuthorization(
                "device-1", "ABCD-EFGH", "http://store.test/device",
                "http://store.test/device?user_code=ABCD-EFGH", 600, 5));
        return service.startConnect(null);
    }

    private MarketplaceConnection connection(String accessToken, long expiresAtSeconds, String refreshToken) {
        return new MarketplaceConnection(accessToken, expiresAtSeconds, refreshToken, "catalog:read",
                "dev@ujcms.com", null, "Dev", "inst-1", "UJCMS 11.0.2", "2026-07-18T10:00:00+08:00", false);
    }

    // ----- 设备授权流 -----

    @Test
    void startConnectDefaultsInstanceNameToVersion() {
        DeviceStart start = startFlow();
        assertEquals("ABCD-EFGH", start.userCode());
        assertEquals(5, start.interval());
        verify(client).requestDeviceCode("UJCMS 11.0.2", null);
    }

    @Test
    void pollHonorsAdvertisedIntervalWithoutHittingTheStore() {
        startFlow();
        // 未到通告间隔：不打市场，直接 pending
        assertEquals(PollStatus.PENDING, service.pollConnect().status());
        verify(client, never()).pollDeviceToken(anyString());

        when(client.pollDeviceToken("device-1")).thenThrow(
                new MarketplaceOAuthException(MarketplaceOAuthException.AUTHORIZATION_PENDING, null));
        advanceSeconds(5);
        assertEquals(PollStatus.PENDING, service.pollConnect().status());
        verify(client, times(1)).pollDeviceToken("device-1");
        // 刚打过：下一轮间隔内的轮询不再打市场
        assertEquals(PollStatus.PENDING, service.pollConnect().status());
        verify(client, times(1)).pollDeviceToken("device-1");
    }

    @Test
    void slowDownRaisesIntervalByFiveSeconds() {
        startFlow();
        when(client.pollDeviceToken("device-1")).thenThrow(
                new MarketplaceOAuthException(MarketplaceOAuthException.SLOW_DOWN, null));
        advanceSeconds(5);
        assertEquals(PollStatus.PENDING, service.pollConnect().status());
        verify(client, times(1)).pollDeviceToken("device-1");
        // slow_down 后间隔 5→10：再过 5 秒仍不打
        advanceSeconds(5);
        assertEquals(PollStatus.PENDING, service.pollConnect().status());
        verify(client, times(1)).pollDeviceToken("device-1");
        // 满 10 秒才再打
        advanceSeconds(5);
        assertEquals(PollStatus.PENDING, service.pollConnect().status());
        verify(client, times(2)).pollDeviceToken("device-1");
    }

    @Test
    void approvalPersistsConnectionWithAccountInfo() {
        startFlow();
        when(client.pollDeviceToken("device-1")).thenReturn(new OAuthTokens(
                "access-1", "Bearer", 900, "refresh-1", "catalog:read entitlements:read download"));
        when(client.getMe("access-1")).thenReturn(new Me(
                new MeAccount("acc-1", "dev@ujcms.com", "+8613800000000", "Dev"),
                new MeInstance("inst-1", "UJCMS 11.0.2", null, "11.0.2", null)));
        advanceSeconds(5);
        PollResult result = service.pollConnect();
        assertEquals(PollStatus.CONNECTED, result.status());
        // 投影脱敏（C4a / C4c）：轮询结果与状态投影只见 dev***@ujcms.com / 138****0000
        assertEquals("d***@ujcms.com", result.accountEmail());
        assertEquals("138****0000", result.accountPhone());

        MarketplaceConnection saved = stored.get();
        assertNotNull(saved);
        assertEquals("access-1", saved.accessToken());
        assertEquals("refresh-1", saved.refreshToken());
        assertEquals(nowSeconds() + 900, saved.accessTokenExpiresAt());
        // 行里存 /me 返回的原值
        assertEquals("dev@ujcms.com", saved.accountEmail());
        assertEquals("+8613800000000", saved.accountPhone());
        assertFalse(saved.reconnectRequired());
        // 挂起流已消费：再轮询 → NONE
        assertEquals(PollStatus.NONE, service.pollConnect().status());
    }

    @Test
    void phoneOnlyAccountIsStoredRawAndProjectedMasked() {
        // 生产账号多为仅手机号（市场 D-M9-7）：email 为 null，控制台芯片回退到脱敏手机号（C4a）
        startFlow();
        when(client.pollDeviceToken("device-1")).thenReturn(new OAuthTokens(
                "access-1", "Bearer", 900, "refresh-1", "catalog:read entitlements:read download"));
        when(client.getMe("access-1")).thenReturn(new Me(
                new MeAccount("acc-2", null, "+8613912345678", "微信用户"), null));
        advanceSeconds(5);
        PollResult result = service.pollConnect();
        assertEquals(PollStatus.CONNECTED, result.status());
        assertNull(result.accountEmail());
        assertEquals("139****5678", result.accountPhone());
        assertEquals("微信用户", result.accountName());

        assertEquals("+8613912345678", stored.get().accountPhone());
        ConnectionStatus status = service.getStatus();
        assertTrue(status.connected());
        assertNull(status.accountEmail());
        assertEquals("139****5678", status.accountPhone());
        assertEquals("微信用户", status.accountName());
    }

    @Test
    void storedConnectionWithoutPhoneFieldStillDeserializes() throws Exception {
        // C4a 之前落库的 JSON 没有 accountPhone：缺失组件填 null，其余字段照旧（无迁移）
        String legacy = """
                {"accessToken":"access-1","accessTokenExpiresAt":1700000000,"refreshToken":"refresh-1",
                 "scope":"catalog:read","accountEmail":"dev@ujcms.com","accountName":"Dev",
                 "instanceId":"inst-1","instanceName":"UJCMS 11.0.2",
                 "connectedAt":"2026-07-18T10:00:00+08:00","reconnectRequired":false}
                """;
        MarketplaceConnection connection = Constants.MAPPER.readValue(legacy, MarketplaceConnection.class);
        assertEquals("dev@ujcms.com", connection.accountEmail());
        assertNull(connection.accountPhone());
        assertEquals("Dev", connection.accountName());
        assertEquals("refresh-1", connection.refreshToken());
        assertFalse(connection.reconnectRequired());

        stored.set(connection);
        ConnectionStatus status = service.getStatus();
        assertEquals("d***@ujcms.com", status.accountEmail());
        assertNull(status.accountPhone());

        // 新行往返：手机号原值落库、读回不变
        MarketplaceConnection withPhone = new MarketplaceConnection("a", 1L, "r", null, null, "+8613800000000",
                "微信用户", null, null, null, false);
        MarketplaceConnection roundTrip = Constants.MAPPER.readValue(
                Constants.MAPPER.writeValueAsString(withPhone), MarketplaceConnection.class);
        assertEquals("+8613800000000", roundTrip.accountPhone());
        assertEquals("+8613800000000", roundTrip.asReconnectRequired().accountPhone());
        assertEquals("+8613800000000", roundTrip.withTokens("a2", 2L, "r2").accountPhone());
    }

    @Test
    void purchaseEnabledFollowsThePersistedScope() throws Exception {
        // C4b / D-C4-4：C4b 之前落库的连接（三个 scope）照常反序列化，但 purchaseEnabled=false——购买对话框提示重新连接
        String legacy = """
                {"accessToken":"access-1","accessTokenExpiresAt":1700000000,"refreshToken":"refresh-1",
                 "scope":"download entitlements:read catalog:read","accountEmail":"dev@ujcms.com",
                 "accountName":"Dev","instanceId":"inst-1","instanceName":"UJCMS 11.0.2",
                 "connectedAt":"2026-07-18T10:00:00+08:00","reconnectRequired":false}
                """;
        stored.set(Constants.MAPPER.readValue(legacy, MarketplaceConnection.class));
        ConnectionStatus legacyStatus = service.getStatus();
        assertTrue(legacyStatus.connected());
        assertFalse(legacyStatus.reconnectRequired());
        assertFalse(legacyStatus.purchaseEnabled());
        assertFalse(service.isPurchaseEnabled());

        // 新连接（C4b 起请求四个 scope；服务端返回不保序）→ true
        startFlow();
        when(client.pollDeviceToken("device-1")).thenReturn(new OAuthTokens(
                "access-2", "Bearer", 900, "refresh-2", "download entitlements:read orders:write catalog:read"));
        when(client.getMe("access-2")).thenReturn(new Me(new MeAccount("acc-1", "dev@ujcms.com", null, "Dev"), null));
        advanceSeconds(5);
        assertEquals(PollStatus.CONNECTED, service.pollConnect().status());
        assertTrue(service.getStatus().purchaseEnabled());
        assertTrue(service.isPurchaseEnabled());
        // 令牌轮换不改变 scope 判定；失效标记保留 scope（重连提示优先于购买提示由调用方决定）
        stored.set(stored.get().withTokens("access-3", nowSeconds() + 900, "refresh-3"));
        assertTrue(service.isPurchaseEnabled());

        // 未连接 → false
        stored.set(null);
        assertFalse(service.getStatus().purchaseEnabled());
        assertFalse(service.isPurchaseEnabled());
    }

    @Test
    void hasOrdersWriteScopeIsTokenExactAndOrderInsensitive() {
        assertTrue(MarketplaceConnectionService.hasOrdersWriteScope("orders:write"));
        assertTrue(MarketplaceConnectionService.hasOrdersWriteScope("catalog:read  orders:write download"));
        assertFalse(MarketplaceConnectionService.hasOrdersWriteScope("catalog:read entitlements:read download"));
        assertFalse(MarketplaceConnectionService.hasOrdersWriteScope("orders:writer"));
        assertFalse(MarketplaceConnectionService.hasOrdersWriteScope(null));
        assertFalse(MarketplaceConnectionService.hasOrdersWriteScope("  "));
    }

    @Test
    void maskPhoneFollowsTheStorefrontRule() {
        assertEquals("138****0000", MarketplaceConnectionService.maskPhone("+8613800000000"));
        assertEquals("+44****0123", MarketplaceConnectionService.maskPhone("+447700900123"));
        assertEquals("+1****4567", MarketplaceConnectionService.maskPhone("+1234567"));
        assertEquals("*******", MarketplaceConnectionService.maskPhone("1234567"));
        assertNull(MarketplaceConnectionService.maskPhone(null));
        assertNull(MarketplaceConnectionService.maskPhone("  "));
    }

    @Test
    void maskEmailKeepsTheDomain() {
        assertEquals("122***@qq.com", MarketplaceConnectionService.maskEmail("122513110@qq.com"));
        assertEquals("d***@ujcms.com", MarketplaceConnectionService.maskEmail("dev@ujcms.com"));
        assertEquals("a***@ujcms.com", MarketplaceConnectionService.maskEmail("ab@ujcms.com"));
        assertEquals("*****", MarketplaceConnectionService.maskEmail("noat!"));
        assertNull(MarketplaceConnectionService.maskEmail(null));
        assertNull(MarketplaceConnectionService.maskEmail("  "));
    }

    @Test
    void deniedAndExpiredEndTheFlow() {
        startFlow();
        doThrow(new MarketplaceOAuthException(MarketplaceOAuthException.ACCESS_DENIED, null))
                .when(client).pollDeviceToken("device-1");
        advanceSeconds(5);
        assertEquals(PollStatus.DENIED, service.pollConnect().status());
        assertEquals(PollStatus.NONE, service.pollConnect().status());

        startFlow();
        doThrow(new MarketplaceOAuthException(MarketplaceOAuthException.EXPIRED_TOKEN, null))
                .when(client).pollDeviceToken("device-1");
        advanceSeconds(5);
        assertEquals(PollStatus.EXPIRED, service.pollConnect().status());
    }

    @Test
    void deviceCodeExpiryEndsFlowWithoutNetwork() {
        startFlow();
        advanceSeconds(600);
        assertEquals(PollStatus.EXPIRED, service.pollConnect().status());
        verify(client, never()).pollDeviceToken(anyString());
    }

    // ----- 令牌刷新（轮换纪律） -----

    @Test
    void expiredTokenRefreshesAndPersistsRotatedPairBeforeUse() {
        stored.set(connection("access-1", nowSeconds() + 10, "refresh-1"));
        when(client.refreshAccessToken("refresh-1")).thenReturn(new OAuthTokens(
                "access-2", "Bearer", 900, "refresh-2", null));

        String used = service.withAccessToken(token -> {
            // 回调执行时新令牌对必须已落库（refresh token 单次有效——崩溃也不能丢新对）
            MarketplaceConnection persisted = stored.get();
            assertEquals("access-2", persisted.accessToken());
            assertEquals("refresh-2", persisted.refreshToken());
            return token;
        });
        assertEquals("access-2", used);
    }

    @Test
    void unauthorizedForcesRefreshAndRetriesOnce() {
        stored.set(connection("access-1", nowSeconds() + 900, "refresh-1"));
        when(client.refreshAccessToken("refresh-1")).thenReturn(new OAuthTokens(
                "access-2", "Bearer", 900, "refresh-2", null));
        List<String> calls = new java.util.ArrayList<>();

        String result = service.withAccessToken(token -> {
            calls.add(token);
            if (calls.size() == 1) {
                // 本地未过期但市场已重启（签名密钥轮换）→ 401
                throw new MarketplaceAuthException(MarketplaceAuthException.Kind.UNAUTHORIZED, "401");
            }
            return "ok:" + token;
        });
        assertEquals("ok:access-2", result);
        assertEquals(List.of("access-1", "access-2"), calls);
        assertEquals("refresh-2", stored.get().refreshToken());
    }

    @Test
    void invalidGrantMarksReconnectRequiredTerminally() {
        stored.set(connection("access-1", nowSeconds() + 10, "refresh-1"));
        when(client.refreshAccessToken("refresh-1")).thenThrow(
                new MarketplaceOAuthException(MarketplaceOAuthException.INVALID_GRANT, "revoked"));

        MarketplaceException e = assertThrows(MarketplaceException.class,
                () -> service.withAccessToken(token -> token));
        assertEquals("marketplace.error.reconnectRequired", e.getMessage());
        assertTrue(stored.get().reconnectRequired());
        // 终局态：后续调用不再打刷新端点，直接提示重连
        assertThrows(MarketplaceException.class, () -> service.withAccessToken(token -> token));
        verify(client, times(1)).refreshAccessToken(anyString());
    }

    @Test
    void insufficientScopeMarksReconnectRequired() {
        stored.set(connection("access-1", nowSeconds() + 900, "refresh-1"));

        MarketplaceException e = assertThrows(MarketplaceException.class, () -> service.withAccessToken(token -> {
            throw new MarketplaceAuthException(MarketplaceAuthException.Kind.INSUFFICIENT_SCOPE, "scope");
        }));
        assertEquals("marketplace.error.reconnectRequired", e.getMessage());
        assertTrue(stored.get().reconnectRequired());
        verify(client, never()).refreshAccessToken(anyString());
    }

    @Test
    void notConnectedRefusesTokenCalls() {
        MarketplaceException e = assertThrows(MarketplaceException.class,
                () -> service.withAccessToken(token -> token));
        assertEquals("marketplace.error.notConnected", e.getMessage());
    }

    // ----- 断开 -----

    @Test
    void disconnectRevokesRemotelyAndClears() {
        stored.set(connection("access-1", nowSeconds() + 900, "refresh-1"));
        assertTrue(service.disconnect().remoteRevoked());
        verify(client).revokeToken("refresh-1", "refresh_token");
        assertNull(stored.get());
    }

    @Test
    void disconnectClearsLocallyEvenWhenRevocationFails() {
        stored.set(connection("access-1", nowSeconds() + 900, "refresh-1"));
        doThrow(new MarketplaceUnavailableException(MarketplaceUnavailableException.Reason.OFFLINE,
                "down", (Throwable) null)).when(client).revokeToken(anyString(), any());
        assertFalse(service.disconnect().remoteRevoked());
        assertNull(stored.get());
    }

    // ----- 授权快照 -----

    @Test
    void listEntitlementsAggregatesPages() {
        stored.set(connection("access-1", nowSeconds() + 900, "refresh-1"));
        when(client.listEntitlements("access-1", 1, 100)).thenReturn(new EntitlementPage(1, 100, 120, 2,
                List.of(new Entitlement("edu-campus", "智慧校园", "grant", null))));
        when(client.listEntitlements("access-1", 2, 100)).thenReturn(new EntitlementPage(2, 100, 120, 2,
                List.of(new Entitlement("enterprise-portal", "企业门户", "order", null))));

        EntitlementsSnapshot snapshot = service.listEntitlements();
        assertTrue(snapshot.connected());
        assertFalse(snapshot.reconnectRequired());
        assertEquals(List.of("edu-campus", "enterprise-portal"),
                snapshot.entitlements().stream().map(Entitlement::item).toList());
    }

    @Test
    void entitlementsWhenDisconnectedIsEmptySnapshot() {
        EntitlementsSnapshot snapshot = service.listEntitlements();
        assertFalse(snapshot.connected());
        assertTrue(snapshot.entitlements().isEmpty());
    }

}
