package com.ujcms.cms.core.marketplace;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongSupplier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ujcms.cms.core.marketplace.MarketplaceClient.OrderCreation;
import com.ujcms.cms.core.marketplace.MarketplaceClient.OrderLine;
import com.ujcms.cms.core.marketplace.MarketplaceClient.PaymentInitiation;
import com.ujcms.cms.core.marketplace.MarketplaceClient.StoreOrder;
import com.ujcms.cms.core.marketplace.MarketplaceConnectionService.ConnectionStatus;
import com.ujcms.common.web.exception.Http409Exception;

/**
 * 控制台内购买（remote-marketplace C4b / D-C4-3；市场 D-M14-2..5）：流程与连接对话框同形——
 * {@code start}（下单 → 取支付方式 → 发起支付）→ {@code poll}（问市场 …/sync）→ 终态
 * PAID / CANCELLED / EXPIRED；{@code cancel} 关单取消。钱不经 CMS：控制台只渲染渠道二维码或新标签页打开渠道页面，
 * 结算由渠道回调 / 市场的订单查询完成，协议、发票、退款、订单历史仍在店面（D-K5 经 D-M14-5 修订，未废除）。
 *
 * <p>行为要点：
 * <ul>
 * <li><b>订单号不是凭据</b>（替人付款是送礼）：{@code orderNo} 经控制台往返，CMS 不像设备码那样持有「挂起购买」；
 * 重启 / 集群另一节点什么都不丢。</li>
 * <li><b>轮询节流</b>（D-C2-2 纪律）：控制台 2 秒一问，CMS 对同一订单最多每 {@value #SYNC_INTERVAL_MILLIS} 毫秒
 * 问一次市场 …/sync（服务端再按订单节流渠道查询）；间隔内复用本节点记住的上一次答案——这份记忆只是节流缓存，
 * 丢了就多问一次。</li>
 * <li><b>EXPIRED 由 CMS 推导</b>：服务端没有过期状态（未付订单一直是 created），控制台把 start 拿到的
 * {@code expiresAt} 原样带回 poll，过期且仍 created 即 EXPIRED；「重新发起」= 再次 start——同条目折叠回同一张
 * 未付订单（D-M14-3），再 …/pay 得到新的支付载荷（新码、新 expiresAt）。</li>
 * <li><b>C4b 之前的连接</b>（持久化 scope 缺 {@code orders:write}，D-C4-4）：start 应答 409（重连提示），
 * 从不 403；连接已失效（reconnectRequired）同样 409。</li>
 * <li><b>订单面的业务拒绝</b>是类型化结果：409 已拥有 / 422 订单状态已变 / 404 / 400 未知支付方式 →
 * 可诉的 {@code marketplace.error.*} 400；取消遭 503（渠道无法确认关单）→ {@link CancelStatus#UNCONFIRMED}
 * 「订单保留」，买家已付 → PAID。</li>
 * </ul>
 */
@Service
public class MarketplacePurchaseService {
    private static final Logger logger = LoggerFactory.getLogger(MarketplacePurchaseService.class);

    /** 同一订单向市场 …/sync 询问的最小间隔（毫秒）——控制台的 2 秒轮询由本节流吸收 */
    static final long SYNC_INTERVAL_MILLIS = 5_000;
    /** 节流记忆上限（同时在付的订单数远小于此；只是防御性封顶） */
    private static final long MEMO_MAX_ENTRIES = 1_000;
    /** 节流记忆保留时长：微信 Native 码 2 小时有效，对话框关闭后记忆无用 */
    private static final long MEMO_TTL_HOURS = 3;
    /** 店面「我的订单」路径（订单与发票在店面查看，D-M14-5） */
    private static final String STOREFRONT_ORDERS_PATH = "/account/orders";
    private static final String ERROR_ORDER_NOT_FOUND = "marketplace.error.orderNotFound";

    private final MarketplaceClient client;
    private final MarketplaceConnectionService connectionService;
    private final String storefrontUrl;
    private final LongSupplier currentTimeMillis;
    private final Cache<String, SyncMemo> memos;

    @Autowired
    public MarketplacePurchaseService(MarketplaceClient client, MarketplaceConnectionService connectionService,
                                      @Value("${ujcms.marketplace.storefront-url:${ujcms.marketplace.base-url:https://market.ujcms.com}}")
                                      String storefrontUrl) {
        this(client, connectionService, storefrontUrl, System::currentTimeMillis);
    }

    /** 测试接缝：可控时钟 */
    MarketplacePurchaseService(MarketplaceClient client, MarketplaceConnectionService connectionService,
                               String storefrontUrl, LongSupplier currentTimeMillis) {
        this.client = client;
        this.connectionService = connectionService;
        this.storefrontUrl = storefrontUrl.endsWith("/")
                ? storefrontUrl.substring(0, storefrontUrl.length() - 1) : storefrontUrl;
        this.currentTimeMillis = currentTimeMillis;
        this.memos = Caffeine.newBuilder()
                .maximumSize(MEMO_MAX_ENTRIES)
                .expireAfterAccess(MEMO_TTL_HOURS, TimeUnit.HOURS)
                .build();
    }

    /**
     * 发起购买：取支付方式（为空 → 请去店面）→ 下单（201 新单 / 200 折叠回同条目的未付单，都付这张）→ 以选定的
     * 支付方式发起支付。{@code provider} 为 null 或不在列表内取第一项（展示序，dev 为 mockpay）；对话框的「更换支付方式」
     * 与过期后的「重新发起」都再次调用本方法。{@code redirect} 的 {@code url} 相对市场 base-url 解析为绝对地址，浏览器可直接打开。
     * 市场不可达 → {@link MarketplaceUnavailableException}（控制器以可用性信封呈现离线态，与连接对话框一致）。
     */
    public PurchaseStart start(String slug, @Nullable String provider) {
        requirePurchaseScope();
        List<String> providers = withOrderToken(client::listPaymentProviders, ERROR_ORDER_NOT_FOUND);
        if (providers.isEmpty()) {
            throw new MarketplaceException("marketplace.error.noPaymentProvider",
                    "Marketplace lists no payment provider; buy on the storefront");
        }
        OrderCreation creation = withOrderToken(token -> client.createOrder(token, slug),
                "marketplace.error.itemNotFound");
        StoreOrder order = creation.order();
        String chosen = provider != null && providers.contains(provider) ? provider : providers.get(0);
        PaymentInitiation initiation = withOrderToken(token -> client.payOrder(token, order.orderNo(), chosen),
                ERROR_ORDER_NOT_FOUND);
        // 新发起：下一次轮询立刻问市场，不沿用旧答案
        memos.invalidate(order.orderNo());
        logger.info("Marketplace purchase started: item={} order={} created={} provider={} kind={}",
                slug, order.orderNo(), creation.created(), initiation.provider(), initiation.kind());
        return new PurchaseStart(order.orderNo(), itemName(order, slug), order.amountCents(),
                initiation.provider(), providers, initiation.kind(), initiation.qrContent(),
                resolveUrl(initiation.url()), initiation.expiresAt(), storefrontUrl + STOREFRONT_ORDERS_PATH);
    }

    /**
     * 轮询订单：间隔内直接按记住的上一次答案推导；到点才问市场 …/sync（市场瞬时不可达 → 沿用上一次答案，
     * 下一个间隔再试）。{@code expiresAt} 是 start 发给控制台、控制台原样带回的支付载荷过期时刻——过期且订单仍
     * created 即 EXPIRED；缺省（redirect 类无过期）则只在 PENDING / PAID / CANCELLED 之间。
     */
    public PurchasePoll poll(String orderNo, String provider, @Nullable String expiresAt) {
        long now = currentTimeMillis.getAsLong();
        SyncMemo memo = memos.getIfPresent(orderNo);
        StoreOrder order = memo == null ? null : memo.order();
        if (memo == null || now - memo.syncedAtMillis() >= SYNC_INTERVAL_MILLIS) {
            try {
                order = withOrderToken(token -> client.syncOrder(token, orderNo, provider), ERROR_ORDER_NOT_FOUND);
            } catch (MarketplaceUnavailableException e) {
                logger.debug("Marketplace unreachable while syncing order {} (keeping the last answer): {}",
                        orderNo, e.getMessage());
            } finally {
                // 失败也记下这次尝试：不可达的市场不被 2 秒一次地敲
                memos.put(orderNo, new SyncMemo(now, order));
            }
        }
        return new PurchasePoll(deriveStatus(order, expiresAt, now));
    }

    /**
     * 取消未支付订单（市场先关单再取消，D-M12-5）：应答 CANCELLED；买家已付则 PAID（控制台按已购处理）；
     * 渠道暂时无法确认关单（503，订单保留 created）→ UNCONFIRMED，控制台提示「支付渠道暂时无法确认，订单保留」；
     * 422（订单已不在 created）→ 重读订单按实际状态应答。
     */
    public PurchaseCancel cancel(String orderNo) {
        long now = currentTimeMillis.getAsLong();
        StoreOrder order;
        try {
            order = connectionService.withAccessToken(token -> client.cancelOrder(token, orderNo));
        } catch (MarketplaceOrderException e) {
            switch (e.getKind()) {
                case PAYMENT_UNAVAILABLE -> {
                    logger.warn("Marketplace could not confirm the payment close for order {} (order kept): {}",
                            orderNo, e.getMessage());
                    return new PurchaseCancel(CancelStatus.UNCONFIRMED);
                }
                case INVARIANT_VIOLATION -> order = withOrderToken(token -> client.getOrder(token, orderNo),
                        ERROR_ORDER_NOT_FOUND);
                default -> throw translate(e, ERROR_ORDER_NOT_FOUND);
            }
        } catch (MarketplaceUnavailableException e) {
            throw asActionError(e);
        }
        memos.put(orderNo, new SyncMemo(now, order));
        logger.info("Marketplace purchase cancel: order={} status={}", orderNo, order.status());
        if (order.isPaid()) {
            return new PurchaseCancel(CancelStatus.PAID);
        }
        // 422 后重读仍是 created：渠道报告的付款与订单不符，订单保留
        return new PurchaseCancel(order.isCreated() ? CancelStatus.UNCONFIRMED : CancelStatus.CANCELLED);
    }

    // ----- helpers ----------------------------------------------------------

    /** 购买前置：已连接；连接失效或持久化 scope 缺 orders:write → 409（重连提示，D-C4-4），绝不 403 */
    private void requirePurchaseScope() {
        ConnectionStatus status = connectionService.getStatus();
        if (!status.connected()) {
            throw new MarketplaceException("marketplace.error.notConnected", "No marketplace account connected");
        }
        if (status.reconnectRequired()) {
            throw new Http409Exception("marketplace.error.reconnectRequired");
        }
        if (!status.purchaseEnabled()) {
            throw new Http409Exception("marketplace.error.purchaseScopeMissing");
        }
    }

    /** 带令牌的订单面调用（透明刷新 / 401 重试 / insufficient_scope → 重连提示在 withAccessToken），业务拒绝译为可诉 400 */
    private <T> T withOrderToken(Function<String, T> call, String notFoundCode) {
        try {
            return connectionService.withAccessToken(call);
        } catch (MarketplaceOrderException e) {
            throw translate(e, notFoundCode);
        }
    }

    private static MarketplaceException translate(MarketplaceOrderException e, String notFoundCode) {
        String code = switch (e.getKind()) {
            case NOT_FOUND -> notFoundCode;
            case ALREADY_OWNED -> "marketplace.error.alreadyOwned";
            case INVARIANT_VIOLATION -> "marketplace.error.orderInvalid";
            case PAYMENT_UNAVAILABLE, BAD_REQUEST -> "marketplace.error.paymentUnavailable";
        };
        return new MarketplaceException(code, e.getMessage(), e);
    }

    private static MarketplaceException asActionError(MarketplaceUnavailableException e) {
        if (e.getReason() == MarketplaceUnavailableException.Reason.RATE_LIMITED) {
            return new MarketplaceException("marketplace.error.rateLimited", e.getMessage(), e);
        }
        return new MarketplaceException("marketplace.error.offline", e.getMessage(), e);
    }

    /** 轮询状态推导：无答案 → PENDING；paid → PAID；cancelled / refunded → CANCELLED；created → 过期则 EXPIRED 否则 PENDING */
    static PurchaseStatus deriveStatus(@Nullable StoreOrder order, @Nullable String expiresAt, long nowMillis) {
        if (order == null) {
            return PurchaseStatus.PENDING;
        }
        if (order.isPaid()) {
            return PurchaseStatus.PAID;
        }
        if (StoreOrder.STATUS_CANCELLED.equals(order.status()) || StoreOrder.STATUS_REFUNDED.equals(order.status())) {
            return PurchaseStatus.CANCELLED;
        }
        return isExpired(expiresAt, nowMillis) ? PurchaseStatus.EXPIRED : PurchaseStatus.PENDING;
    }

    /** 支付载荷过期判断（ISO-8601 带时区；不可解析视为不过期——宁可多等不可误判） */
    static boolean isExpired(@Nullable String expiresAt, long nowMillis) {
        if (expiresAt == null || expiresAt.isBlank()) {
            return false;
        }
        try {
            return OffsetDateTime.parse(expiresAt.trim()).toInstant().toEpochMilli() <= nowMillis;
        } catch (DateTimeParseException e) {
            logger.warn("Unparseable payment expiresAt '{}'; treating as not expired", expiresAt);
            return false;
        }
    }

    private static String itemName(StoreOrder order, String slug) {
        List<OrderLine> items = order.items();
        if (items == null || items.isEmpty() || items.get(0).name() == null || items.get(0).name().isBlank()) {
            return slug;
        }
        return items.get(0).name();
    }

    /** redirect 载荷的 url：相对引用按市场 base-url 解析（MockPay 的 /api/pay/…），绝对地址（支付宝收银台）原样 */
    @Nullable
    private String resolveUrl(@Nullable String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return client.getBaseUrl() + (trimmed.startsWith("/") ? trimmed : "/" + trimmed);
    }

    // ----- 类型 --------------------------------------------------------------

    /** 轮询状态（EXPIRED 为 CMS 推导：支付载荷过期而订单仍 created） */
    public enum PurchaseStatus {
        /** 等待买家付款（或未到间隔 / 市场瞬时不可达） */
        PENDING,
        /** 已付款——授权已到账，控制台刷新授权即解锁安装 */
        PAID,
        /** 订单已取消（或已退款） */
        CANCELLED,
        /** 支付载荷已过期而订单仍未付：重新发起（再次 start 折叠回同一订单并得到新载荷） */
        EXPIRED
    }

    /** 取消结果 */
    public enum CancelStatus {
        /** 已取消 */
        CANCELLED,
        /** 买家已付款，订单未取消（按已购处理） */
        PAID,
        /** 渠道暂时无法确认，订单保留（仍可付 / 稍后再取消） */
        UNCONFIRMED
    }

    /**
     * 购买发起结果（购买对话框的数据源）。{@code kind=qrcode} → 就地渲染 {@code qrContent}；{@code kind=redirect} →
     * 「前往支付」新标签页打开 {@code url}（已解析为绝对地址）；{@code providers} 多于一项时对话框提供「更换支付方式」；
     * {@code storefrontOrderUrl} 为店面「我的订单」（订单与发票在店面查看）。
     */
    public record PurchaseStart(String orderNo, String itemName, long amountCents, String provider,
                                List<String> providers, String kind, @Nullable String qrContent,
                                @Nullable String url, @Nullable String expiresAt, String storefrontOrderUrl) {
    }

    /** 轮询结果 */
    public record PurchasePoll(PurchaseStatus status) {
    }

    /** 取消结果 */
    public record PurchaseCancel(CancelStatus status) {
    }

    /** 每订单的节流记忆：上次问市场的时刻 + 答案（失败时沿用之前的答案，可为 null） */
    private record SyncMemo(long syncedAtMillis, @Nullable StoreOrder order) {
    }
}
