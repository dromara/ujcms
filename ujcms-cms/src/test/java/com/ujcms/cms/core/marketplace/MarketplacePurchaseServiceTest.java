package com.ujcms.cms.core.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
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

import com.ujcms.cms.core.marketplace.MarketplaceClient.OrderCreation;
import com.ujcms.cms.core.marketplace.MarketplaceClient.OrderLine;
import com.ujcms.cms.core.marketplace.MarketplaceClient.PaymentInitiation;
import com.ujcms.cms.core.marketplace.MarketplaceClient.StoreOrder;
import com.ujcms.cms.core.marketplace.MarketplaceOrderException.Kind;
import com.ujcms.cms.core.marketplace.MarketplacePurchaseService.CancelStatus;
import com.ujcms.cms.core.marketplace.MarketplacePurchaseService.PurchaseStart;
import com.ujcms.cms.core.marketplace.MarketplacePurchaseService.PurchaseStatus;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.web.exception.Http409Exception;

/**
 * {@link MarketplacePurchaseService} 行为回归（C4b / D-C4-3/4）：start 的下单 → 支付方式 → 发起支付链（201 与折叠的 200
 * 同样处理、首项预选 / 指定项、redirect url 相对 base-url 解析）、poll 的每订单 5 秒 …/sync 节流（可控时钟，不 sleep）与
 * PENDING / PAID / CANCELLED / EXPIRED 推导、C4b 之前连接的 409、取消的 CANCELLED / PAID / UNCONFIRMED（503）。
 * 连接服务是真实实例 + 内存假存储（purchaseEnabled 从持久化 scope 推导），市场客户端为 mock。
 */
class MarketplacePurchaseServiceTest {
    private static final String BASE = "https://store.test";
    private static final String ORDER_NO = "MP20261109114545467355";
    private static final String SCOPES_C4B = "download entitlements:read orders:write catalog:read";
    private static final String SCOPES_C2 = "download entitlements:read catalog:read";

    private MarketplaceClient client;
    private final AtomicReference<MarketplaceConnection> stored = new AtomicReference<>();
    private final long[] nowMillis = {1_700_000_000_000L};
    private MarketplacePurchaseService service;

    @BeforeEach
    void setUp() {
        client = mock(MarketplaceClient.class);
        when(client.getBaseUrl()).thenReturn(BASE);
        MarketplaceConnectionStore store = mock(MarketplaceConnectionStore.class);
        when(store.load()).thenAnswer(inv -> stored.get());
        doAnswer(inv -> {
            stored.set(inv.getArgument(0));
            return null;
        }).when(store).save(any());
        Props props = mock(Props.class);
        when(props.getVersion()).thenReturn("13.2.2");
        MarketplaceConnectionService connectionService =
                new MarketplaceConnectionService(client, store, props, () -> nowMillis[0]);
        service = new MarketplacePurchaseService(client, connectionService, "http://localhost:3000/", () -> nowMillis[0]);
        stored.set(connection(SCOPES_C4B, false));
    }

    private MarketplaceConnection connection(String scope, boolean reconnectRequired) {
        return new MarketplaceConnection("at-1", nowMillis[0] / 1000 + 900, "rt-1", scope,
                "dev@ujcms.com", null, "Dev", "1", "UJCMS 13.2.2", "2026-11-09T20:00:00+08:00", reconnectRequired);
    }

    private void advanceSeconds(long seconds) {
        nowMillis[0] += seconds * 1000;
    }

    private static StoreOrder order(String status) {
        return new StoreOrder(ORDER_NO, status, 9900, "2026-11-09T12:00:00Z",
                "paid".equals(status) ? "2026-11-09T12:01:00Z" : null, null, null,
                List.of(new OrderLine("m8-live", "M8 验证主题", 9900)), null);
    }

    private static PaymentInitiation mockPay() {
        return new PaymentInitiation("mockpay", ORDER_NO, "redirect", "/api/pay/mockpay/" + ORDER_NO + "/succeed",
                null, null);
    }

    private void stubHappyStart(List<String> providers, boolean created) {
        when(client.listPaymentProviders("at-1")).thenReturn(providers);
        when(client.createOrder("at-1", "m8-live")).thenReturn(new OrderCreation(order("created"), created));
        when(client.payOrder(eq("at-1"), eq(ORDER_NO), anyString())).thenAnswer(inv -> {
            String provider = inv.getArgument(2);
            return "wechatpay".equals(provider)
                    ? new PaymentInitiation("wechatpay", ORDER_NO, "qrcode", null,
                    "weixin://wxpay/bizpayurl?pr=abc", "2026-11-09T14:00:00Z")
                    : mockPay();
        });
    }

    // ----- start -----

    @Test
    void startCreatesOrderPicksFirstProviderAndResolvesRedirectUrl() {
        stubHappyStart(List.of("mockpay", "wechatpay"), true);

        PurchaseStart start = service.start("m8-live", null);

        assertEquals(ORDER_NO, start.orderNo());
        assertEquals("M8 验证主题", start.itemName());
        assertEquals(9900, start.amountCents());
        // 支付方式列表首项预选（dev 为 mockpay），全部列表随载荷返回供「更换支付方式」
        assertEquals("mockpay", start.provider());
        assertEquals(List.of("mockpay", "wechatpay"), start.providers());
        assertEquals("redirect", start.kind());
        // 相对引用按市场 base-url 解析为绝对地址——浏览器可直接打开
        assertEquals(BASE + "/api/pay/mockpay/" + ORDER_NO + "/succeed", start.url());
        assertNull(start.qrContent());
        assertNull(start.expiresAt());
        // 店面「我的订单」（订单与发票在店面查看）；尾斜杠已去
        assertEquals("http://localhost:3000/account/orders", start.storefrontOrderUrl());
        verify(client).payOrder("at-1", ORDER_NO, "mockpay");
    }

    @Test
    void startAcceptsFoldedOpenOrderAndRequestedProvider() {
        // 200 折叠回同条目的未付订单（市场 D-M14-3）与 201 同样处理；指定的支付方式在列表内即采用
        stubHappyStart(List.of("mockpay", "wechatpay"), false);

        PurchaseStart start = service.start("m8-live", "wechatpay");

        assertEquals("wechatpay", start.provider());
        assertEquals("qrcode", start.kind());
        assertEquals("weixin://wxpay/bizpayurl?pr=abc", start.qrContent());
        assertEquals("2026-11-09T14:00:00Z", start.expiresAt());
        assertNull(start.url());
    }

    @Test
    void startFallsBackToFirstProviderWhenRequestedOneIsUnknownAndKeepsAbsoluteUrls() {
        when(client.listPaymentProviders("at-1")).thenReturn(List.of("alipay"));
        when(client.createOrder("at-1", "m8-live")).thenReturn(new OrderCreation(order("created"), true));
        when(client.payOrder("at-1", ORDER_NO, "alipay")).thenReturn(new PaymentInitiation("alipay", ORDER_NO,
                "redirect", "https://openapi.alipay.com/gateway.do?x=1", null, null));

        PurchaseStart start = service.start("m8-live", "nopay");

        assertEquals("alipay", start.provider());
        // 绝对地址（渠道收银台）原样
        assertEquals("https://openapi.alipay.com/gateway.do?x=1", start.url());
    }

    @Test
    void startWithoutAnyProviderIsActionable() {
        when(client.listPaymentProviders("at-1")).thenReturn(List.of());
        MarketplaceException e = assertThrows(MarketplaceException.class, () -> service.start("m8-live", null));
        assertEquals("marketplace.error.noPaymentProvider", e.getMessage());
        verify(client, never()).createOrder(anyString(), anyString());
    }

    @Test
    void startOnPreC4bConnectionAnswers409NeverCallingTheStore() {
        // 持久化 scope 缺 orders:write（C4b 之前批准的连接，D-C4-4）→ 409 重连提示，不打市场、不是 403
        stored.set(connection(SCOPES_C2, false));
        Http409Exception e = assertThrows(Http409Exception.class, () -> service.start("m8-live", null));
        assertEquals("marketplace.error.purchaseScopeMissing", e.getMessage());
        verify(client, never()).listPaymentProviders(anyString());
        verify(client, never()).createOrder(anyString(), anyString());
    }

    @Test
    void startOnDeadOrMissingConnectionIsReconnectOrNotConnected() {
        stored.set(connection(SCOPES_C4B, true));
        Http409Exception dead = assertThrows(Http409Exception.class, () -> service.start("m8-live", null));
        assertEquals("marketplace.error.reconnectRequired", dead.getMessage());

        stored.set(null);
        MarketplaceException none = assertThrows(MarketplaceException.class, () -> service.start("m8-live", null));
        assertEquals("marketplace.error.notConnected", none.getMessage());
    }

    @Test
    void startTranslatesOrderRejectionsToActionableErrors() {
        when(client.listPaymentProviders("at-1")).thenReturn(List.of("mockpay"));
        when(client.createOrder("at-1", "m8-live"))
                .thenThrow(new MarketplaceOrderException(Kind.ALREADY_OWNED, "already_owned", "owned"));
        assertEquals("marketplace.error.alreadyOwned",
                assertThrows(MarketplaceException.class, () -> service.start("m8-live", null)).getMessage());

        doThrow(new MarketplaceOrderException(Kind.INVARIANT_VIOLATION, "invariant_violation", "free"))
                .when(client).createOrder("at-1", "m8-live");
        assertEquals("marketplace.error.orderInvalid",
                assertThrows(MarketplaceException.class, () -> service.start("m8-live", null)).getMessage());

        doThrow(new MarketplaceOrderException(Kind.NOT_FOUND, "not_found", "draft"))
                .when(client).createOrder("at-1", "m8-live");
        assertEquals("marketplace.error.itemNotFound",
                assertThrows(MarketplaceException.class, () -> service.start("m8-live", null)).getMessage());

        // 发起支付时渠道不可用 → 可诉（更换支付方式 / 去店面）
        doReturn(new OrderCreation(order("created"), true)).when(client).createOrder("at-1", "m8-live");
        when(client.payOrder("at-1", ORDER_NO, "mockpay"))
                .thenThrow(new MarketplaceOrderException(Kind.PAYMENT_UNAVAILABLE, "payment_unavailable", "down"));
        assertEquals("marketplace.error.paymentUnavailable",
                assertThrows(MarketplaceException.class, () -> service.start("m8-live", null)).getMessage());
    }

    @Test
    void startInsufficientScopeFromTheStoreMarksReconnectRequired() {
        // 持久化 scope 看似完整但服务端 403 insufficient_scope（如服务端吊销重发）：既有 withAccessToken 映射覆盖订单面
        when(client.listPaymentProviders("at-1"))
                .thenThrow(new MarketplaceAuthException(MarketplaceAuthException.Kind.INSUFFICIENT_SCOPE, "scope"));
        MarketplaceException e = assertThrows(MarketplaceException.class, () -> service.start("m8-live", null));
        assertEquals("marketplace.error.reconnectRequired", e.getMessage());
        assertTrue(stored.get().reconnectRequired());
    }

    @Test
    void startInvalidatesTheSyncMemoSoTheNextPollAsksTheStore() {
        when(client.syncOrder("at-1", ORDER_NO, "mockpay")).thenReturn(order("created"));
        service.poll(ORDER_NO, "mockpay", null);
        verify(client, times(1)).syncOrder("at-1", ORDER_NO, "mockpay");

        // 2 秒后重新发起（更换支付方式 / 重新发起都折叠回同一订单）：记忆作废，紧接着的轮询立刻问市场
        advanceSeconds(2);
        stubHappyStart(List.of("mockpay"), false);
        service.start("m8-live", null);
        service.poll(ORDER_NO, "mockpay", null);
        verify(client, times(2)).syncOrder("at-1", ORDER_NO, "mockpay");
    }

    // ----- poll：节流 + 推导 -----

    @Test
    void pollAsksTheStoreAtMostOncePerFiveSecondsWhateverTheConsoleCadence() {
        when(client.syncOrder("at-1", ORDER_NO, "mockpay")).thenReturn(order("created"));

        // 控制台 2 秒一问：0 s 问市场；2 s、4 s 复用答案；5 s 再问；7 s 复用
        assertEquals(PurchaseStatus.PENDING, service.poll(ORDER_NO, "mockpay", null).status());
        advanceSeconds(2);
        assertEquals(PurchaseStatus.PENDING, service.poll(ORDER_NO, "mockpay", null).status());
        advanceSeconds(2);
        assertEquals(PurchaseStatus.PENDING, service.poll(ORDER_NO, "mockpay", null).status());
        verify(client, times(1)).syncOrder("at-1", ORDER_NO, "mockpay");

        advanceSeconds(1);
        when(client.syncOrder("at-1", ORDER_NO, "mockpay")).thenReturn(order("paid"));
        assertEquals(PurchaseStatus.PAID, service.poll(ORDER_NO, "mockpay", null).status());
        verify(client, times(2)).syncOrder("at-1", ORDER_NO, "mockpay");

        advanceSeconds(2);
        assertEquals(PurchaseStatus.PAID, service.poll(ORDER_NO, "mockpay", null).status());
        verify(client, times(2)).syncOrder("at-1", ORDER_NO, "mockpay");
    }

    @Test
    void pollThrottleIsPerOrder() {
        when(client.syncOrder(eq("at-1"), anyString(), eq("mockpay"))).thenReturn(order("created"));
        service.poll(ORDER_NO, "mockpay", null);
        service.poll("MP20261109114707394915", "mockpay", null);
        verify(client, times(1)).syncOrder("at-1", ORDER_NO, "mockpay");
        verify(client, times(1)).syncOrder("at-1", "MP20261109114707394915", "mockpay");
    }

    @Test
    void pollDerivesCancelledAndExpired() {
        when(client.syncOrder("at-1", ORDER_NO, "wechatpay")).thenReturn(order("cancelled"));
        assertEquals(PurchaseStatus.CANCELLED, service.poll(ORDER_NO, "wechatpay", null).status());

        // 服务端没有过期状态：码过期而订单仍 created → CMS 推导 EXPIRED（控制台把 start 的 expiresAt 原样带回）
        advanceSeconds(5);
        when(client.syncOrder("at-1", ORDER_NO, "wechatpay")).thenReturn(order("created"));
        String past = java.time.Instant.ofEpochMilli(nowMillis[0] - 1000).toString();
        String future = java.time.Instant.ofEpochMilli(nowMillis[0] + 60_000).toString();
        assertEquals(PurchaseStatus.EXPIRED, service.poll(ORDER_NO, "wechatpay", past).status());
        // 间隔内同一答案、不同 expiresAt：推导只看时钟
        assertEquals(PurchaseStatus.PENDING, service.poll(ORDER_NO, "wechatpay", future).status());
        verify(client, times(2)).syncOrder("at-1", ORDER_NO, "wechatpay");

        // 已付订单从不因 expiresAt 变成过期
        advanceSeconds(5);
        when(client.syncOrder("at-1", ORDER_NO, "wechatpay")).thenReturn(order("paid"));
        assertEquals(PurchaseStatus.PAID, service.poll(ORDER_NO, "wechatpay", past).status());
    }

    @Test
    void pollKeepsTheLastAnswerWhenTheStoreIsUnreachableAndDoesNotHammerIt() {
        when(client.syncOrder("at-1", ORDER_NO, "mockpay")).thenReturn(order("created"));
        service.poll(ORDER_NO, "mockpay", null);

        advanceSeconds(5);
        when(client.syncOrder("at-1", ORDER_NO, "mockpay")).thenThrow(new MarketplaceUnavailableException(
                MarketplaceUnavailableException.Reason.OFFLINE, "down", (Throwable) null));
        assertEquals(PurchaseStatus.PENDING, service.poll(ORDER_NO, "mockpay", null).status());
        // 失败也算一次尝试：2 秒后不再打不可达的市场
        advanceSeconds(2);
        assertEquals(PurchaseStatus.PENDING, service.poll(ORDER_NO, "mockpay", null).status());
        verify(client, times(2)).syncOrder("at-1", ORDER_NO, "mockpay");

        // 首次就不可达：无答案 → PENDING，不炸
        assertEquals(PurchaseStatus.PENDING, service.poll("MP20261109114707394915", "mockpay", null).status());
    }

    @Test
    void pollOnForeignOrderIsActionable() {
        when(client.syncOrder("at-1", ORDER_NO, "mockpay"))
                .thenThrow(new MarketplaceOrderException(Kind.NOT_FOUND, "not_found", "foreign"));
        MarketplaceException e = assertThrows(MarketplaceException.class, () -> service.poll(ORDER_NO, "mockpay", null));
        assertEquals("marketplace.error.orderNotFound", e.getMessage());
    }

    @Test
    void expiryParsingAcceptsIsoOffsetsAndNeverExpiresOnGarbage() {
        long now = 1_700_000_000_000L;
        assertTrue(MarketplacePurchaseService.isExpired("2023-11-14T22:13:19.000Z", now)); // 1 秒前
        assertTrue(MarketplacePurchaseService.isExpired("2023-11-15T06:13:20+08:00", now)); // 恰好此刻（含）
        assertFalse(MarketplacePurchaseService.isExpired("2023-11-14T22:13:21.500000Z", now));
        assertFalse(MarketplacePurchaseService.isExpired(null, now));
        assertFalse(MarketplacePurchaseService.isExpired("  ", now));
        assertFalse(MarketplacePurchaseService.isExpired("not-a-date", now));
    }

    // ----- cancel -----

    @Test
    void cancelAnswersCancelledOrPaid() {
        when(client.cancelOrder("at-1", ORDER_NO)).thenReturn(order("cancelled"));
        assertEquals(CancelStatus.CANCELLED, service.cancel(ORDER_NO).status());
        // 取消后的记忆即刻生效：间隔内的轮询看到 CANCELLED 而不再问市场
        assertEquals(PurchaseStatus.CANCELLED, service.poll(ORDER_NO, "mockpay", null).status());
        verify(client, never()).syncOrder(anyString(), anyString(), anyString());

        // 买家已付款（市场 D-M12-5：关单前查到已支付）→ PAID，控制台按已购处理
        when(client.cancelOrder("at-1", ORDER_NO)).thenReturn(order("paid"));
        assertEquals(CancelStatus.PAID, service.cancel(ORDER_NO).status());
    }

    @Test
    void cancelUnconfirmedCloseKeepsTheOrder() {
        // 503 payment_unavailable：渠道无法确认关单，订单保留 created → 「支付渠道暂时无法确认，订单保留」
        when(client.cancelOrder("at-1", ORDER_NO))
                .thenThrow(new MarketplaceOrderException(Kind.PAYMENT_UNAVAILABLE, "payment_unavailable", "close"));
        assertEquals(CancelStatus.UNCONFIRMED, service.cancel(ORDER_NO).status());
    }

    @Test
    void cancelOnAnOrderNoLongerCreatedReadsItBack() {
        when(client.cancelOrder("at-1", ORDER_NO))
                .thenThrow(new MarketplaceOrderException(Kind.INVARIANT_VIOLATION, "invariant_violation", "paid"));
        when(client.getOrder("at-1", ORDER_NO)).thenReturn(order("paid"));
        assertEquals(CancelStatus.PAID, service.cancel(ORDER_NO).status());

        when(client.getOrder("at-1", ORDER_NO)).thenReturn(order("cancelled"));
        assertEquals(CancelStatus.CANCELLED, service.cancel(ORDER_NO).status());

        // 422 后重读仍 created（渠道报告的付款与订单不符）：订单保留
        when(client.getOrder("at-1", ORDER_NO)).thenReturn(order("created"));
        assertEquals(CancelStatus.UNCONFIRMED, service.cancel(ORDER_NO).status());
    }

    @Test
    void cancelFailuresAreActionable() {
        when(client.cancelOrder("at-1", ORDER_NO))
                .thenThrow(new MarketplaceOrderException(Kind.NOT_FOUND, "not_found", "foreign"));
        assertEquals("marketplace.error.orderNotFound",
                assertThrows(MarketplaceException.class, () -> service.cancel(ORDER_NO)).getMessage());

        doThrow(new MarketplaceUnavailableException(MarketplaceUnavailableException.Reason.RATE_LIMITED, "429", 3))
                .when(client).cancelOrder("at-1", ORDER_NO);
        assertEquals("marketplace.error.rateLimited",
                assertThrows(MarketplaceException.class, () -> service.cancel(ORDER_NO)).getMessage());

        doThrow(new MarketplaceUnavailableException(MarketplaceUnavailableException.Reason.OFFLINE, "down",
                (Throwable) null)).when(client).cancelOrder("at-1", ORDER_NO);
        assertEquals("marketplace.error.offline",
                assertThrows(MarketplaceException.class, () -> service.cancel(ORDER_NO)).getMessage());
    }
}
