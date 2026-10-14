package com.ujcms.cms.core.marketplace;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.LongSupplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ujcms.cms.core.marketplace.MarketplaceClient.DeviceAuthorization;
import com.ujcms.cms.core.marketplace.MarketplaceClient.Entitlement;
import com.ujcms.cms.core.marketplace.MarketplaceClient.EntitlementPage;
import com.ujcms.cms.core.marketplace.MarketplaceClient.Me;
import com.ujcms.cms.core.marketplace.MarketplaceClient.OAuthTokens;
import com.ujcms.cms.core.support.Props;

/**
 * 应用市场账号连接生命周期（remote-marketplace C2）：设备授权流（RFC 8628）连接、
 * 到期/失效透明刷新（refresh token 单次有效、每次轮换——新令牌对先落库再作废旧令牌）、
 * 远程吊销断开。连接部署级唯一、令牌只存服务端（D-C1-1 / D-K3）。
 *
 * <p>行为要点：
 * <ul>
 * <li><b>轮询节流</b>：控制台可任意频率轮询本服务，真正打到市场令牌端点的频率遵守服务端
 * 通告的 {@code interval}，收到 {@code slow_down} 间隔 +5 秒（RFC 8628 §3.5）。</li>
 * <li><b>透明刷新</b>：市场的 JWT 签名密钥在内存中，服务端重启即作废全部在途访问令牌——
 * 本地过期戳只是首道闸门，资源调用遭 401 时强制刷新一次并重试（{@link #withAccessToken}）。</li>
 * <li><b>刷新竞态</b>：轮换令牌单次有效，刷新在进程内串行化；拿到锁后重读持久化连接，
 * 别的请求刚轮换过就直接复用其结果。</li>
 * <li><b>失效终局</b>：刷新遭 {@code invalid_grant} 或资源调用遭 {@code insufficient_scope}
 * （scope 集合变更）→ 置 {@code reconnectRequired}，控制台提示重新连接——不是普通报错。</li>
 * <li><b>待批准状态在内存</b>（单挂起流，新的连接请求顶替旧的）：设备码是凭据、不落库；
 * 对话框关闭或后端重启即弃，令牌到手才持久化。</li>
 * </ul>
 */
@Service
public class MarketplaceConnectionService {
    private static final Logger logger = LoggerFactory.getLogger(MarketplaceConnectionService.class);

    /** 访问令牌过期判断的提前量（秒）：临期视同已过期，避免在途请求撞上边界 */
    private static final long EXPIRY_SKEW_SECONDS = 30;
    /** 授权分页拉取的页数上限（官方目录小体量，防御性封顶） */
    private static final int MAX_ENTITLEMENT_PAGES = 10;

    private final MarketplaceClient client;
    private final MarketplaceConnectionStore store;
    private final Props props;
    private final LongSupplier currentTimeMillis;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private final AtomicReference<@Nullable PendingAuth> pending = new AtomicReference<>();

    @Autowired
    public MarketplaceConnectionService(MarketplaceClient client, MarketplaceConnectionStore store, Props props) {
        this(client, store, props, System::currentTimeMillis);
    }

    /** 测试接缝：可控时钟 */
    MarketplaceConnectionService(MarketplaceClient client, MarketplaceConnectionStore store, Props props,
                                 LongSupplier currentTimeMillis) {
        this.client = client;
        this.store = store;
        this.props = props;
        this.currentTimeMillis = currentTimeMillis;
    }

    // ----- 连接建立（设备授权流） -------------------------------------------

    /**
     * 发起设备授权流。返回给控制台展示 user_code / 批准页链接；device_code 是凭据，
     * 只留在服务端内存（新流顶替旧流）。
     */
    public DeviceStart startConnect(@Nullable String instanceName) {
        String name = instanceName == null || instanceName.isBlank()
                ? "UJCMS " + props.getVersion() : instanceName.trim();
        DeviceAuthorization authorization = client.requestDeviceCode(name, null);
        long now = currentTimeMillis.getAsLong();
        PendingAuth auth = new PendingAuth(authorization.deviceCode(),
                now + authorization.expiresIn() * 1000L, Math.max(1, authorization.interval()));
        auth.nextPollAtMillis = now + auth.intervalSeconds * 1000L;
        pending.set(auth);
        return new DeviceStart(authorization.userCode(), authorization.verificationUri(),
                authorization.verificationUriComplete(), authorization.expiresIn(), authorization.interval());
    }

    /**
     * 轮询连接结果。未到通告间隔时不打市场（直接应答 pending）；批准成功即持久化连接
     * （尽力取 {@code /me} 充实账号信息——取不到不阻塞连接建立）。
     */
    public PollResult pollConnect() {
        PendingAuth auth = pending.get();
        long now = currentTimeMillis.getAsLong();
        if (auth == null) {
            return PollResult.of(PollStatus.NONE);
        }
        if (now >= auth.expiresAtMillis) {
            pending.compareAndSet(auth, null);
            return PollResult.of(PollStatus.EXPIRED);
        }
        if (now < auth.nextPollAtMillis) {
            return PollResult.of(PollStatus.PENDING);
        }
        OAuthTokens tokens;
        try {
            tokens = client.pollDeviceToken(auth.deviceCode);
        } catch (MarketplaceOAuthException e) {
            if (e.is(MarketplaceOAuthException.AUTHORIZATION_PENDING)) {
                auth.nextPollAtMillis = now + auth.intervalSeconds * 1000L;
                return PollResult.of(PollStatus.PENDING);
            }
            if (e.is(MarketplaceOAuthException.SLOW_DOWN)) {
                // RFC 8628 §3.5：轮询过快，通告间隔 +5 秒
                auth.intervalSeconds += 5;
                auth.nextPollAtMillis = now + auth.intervalSeconds * 1000L;
                return PollResult.of(PollStatus.PENDING);
            }
            pending.compareAndSet(auth, null);
            if (e.is(MarketplaceOAuthException.ACCESS_DENIED)) {
                return PollResult.of(PollStatus.DENIED);
            }
            // expired_token / 未知设备码（如市场中途重启）→ 流已死，重新发起
            return PollResult.of(PollStatus.EXPIRED);
        } catch (MarketplaceUnavailableException e) {
            // 市场瞬时不可达：保留挂起流，下一轮再试
            return PollResult.of(PollStatus.PENDING);
        }
        MarketplaceConnection connection = establishConnection(tokens);
        pending.compareAndSet(auth, null);
        return new PollResult(PollStatus.CONNECTED, connection.accountEmail(), connection.accountName());
    }

    private MarketplaceConnection establishConnection(OAuthTokens tokens) {
        long expiresAt = currentTimeMillis.getAsLong() / 1000 + tokens.expiresIn();
        String refreshToken = tokens.refreshToken();
        if (refreshToken == null) {
            // 契约要求设备授权成功必发 refresh token；缺失即无法维持长连接
            throw new MarketplaceException("marketplace.error.connectFailed",
                    "Marketplace token response carries no refresh token");
        }
        String accountEmail = null;
        String accountName = null;
        String instanceId = null;
        String instanceName = null;
        try {
            Me me = client.getMe(tokens.accessToken());
            accountEmail = me.account().email();
            accountName = me.account().displayName();
            if (me.instance() != null) {
                instanceId = me.instance().id();
                instanceName = me.instance().name();
            }
        } catch (RuntimeException e) {
            logger.warn("Marketplace /me lookup failed right after connect (connection kept): {}", e.getMessage());
        }
        MarketplaceConnection connection = new MarketplaceConnection(tokens.accessToken(), expiresAt,
                refreshToken, tokens.scope(), accountEmail, accountName, instanceId, instanceName,
                OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), false);
        store.save(connection);
        logger.info("Marketplace account connected: account={} instance={}", accountEmail, instanceName);
        return connection;
    }

    // ----- 断开 -------------------------------------------------------------

    /**
     * 断开连接：尽力远程吊销 refresh token（吊销即杀连接，RFC 7009 幂等），无论吊销成败
     * 本地连接一律清除——操作员的断开意图必须立即生效，市场不可达不阻塞。
     */
    public DisconnectResult disconnect() {
        pending.set(null);
        MarketplaceConnection connection = store.load();
        if (connection == null) {
            return new DisconnectResult(false);
        }
        boolean remoteRevoked = false;
        try {
            client.revokeToken(connection.refreshToken(), "refresh_token");
            remoteRevoked = true;
        } catch (RuntimeException e) {
            logger.warn("Marketplace remote revocation failed (connection cleared locally anyway): {}", e.getMessage());
        }
        store.clear();
        logger.info("Marketplace account disconnected: account={} remoteRevoked={}",
                connection.accountEmail(), remoteRevoked);
        return new DisconnectResult(remoteRevoked);
    }

    // ----- 状态与令牌 --------------------------------------------------------

    /** 连接状态投影（控制台连接芯片的数据源；绝不含令牌） */
    public ConnectionStatus getStatus() {
        MarketplaceConnection connection = store.load();
        if (connection == null) {
            return new ConnectionStatus(false, false, null, null, null, null);
        }
        return new ConnectionStatus(true, connection.reconnectRequired(), connection.accountEmail(),
                connection.accountName(), connection.instanceName(), connection.connectedAt());
    }

    public boolean isConnected() {
        return store.load() != null;
    }

    /**
     * 以有效访问令牌执行资源调用：到期先刷新；401（服务端密钥轮换）强制刷新后重试一次；
     * {@code insufficient_scope} / 刷新遭 {@code invalid_grant} → 置 reconnectRequired 并抛
     * {@code marketplace.error.reconnectRequired}（重连提示，不是普通报错）。
     */
    public <T> T withAccessToken(Function<String, T> call) {
        MarketplaceConnection connection = store.load();
        if (connection == null) {
            throw new MarketplaceException("marketplace.error.notConnected",
                    "No marketplace account connected");
        }
        if (connection.reconnectRequired()) {
            throw reconnectRequired();
        }
        String token = isExpired(connection) ? refreshTokens(connection.accessToken()) : connection.accessToken();
        try {
            return call.apply(token);
        } catch (MarketplaceAuthException e) {
            if (e.getKind() == MarketplaceAuthException.Kind.INSUFFICIENT_SCOPE) {
                markReconnectRequired();
                throw reconnectRequired();
            }
            // 401：本地未过期但令牌已死（如市场重启换签名密钥）→ 强制刷新重试一次
            String fresh = refreshTokens(token);
            try {
                return call.apply(fresh);
            } catch (MarketplaceAuthException again) {
                markReconnectRequired();
                throw reconnectRequired();
            }
        }
    }

    /** 连接账号的全部有效授权（分页拉全；未连接返回空快照而非报错——目录 UI 的常态数据源） */
    public EntitlementsSnapshot listEntitlements() {
        MarketplaceConnection connection = store.load();
        if (connection == null) {
            return new EntitlementsSnapshot(false, false, List.of());
        }
        if (connection.reconnectRequired()) {
            return new EntitlementsSnapshot(true, true, List.of());
        }
        try {
            List<Entitlement> all = withAccessToken(token -> {
                List<Entitlement> items = new ArrayList<>();
                int page = 1;
                EntitlementPage result;
                do {
                    result = client.listEntitlements(token, page, 100);
                    items.addAll(result.items());
                    page += 1;
                } while (page <= result.totalPages() && page <= MAX_ENTITLEMENT_PAGES);
                return items;
            });
            return new EntitlementsSnapshot(true, false, all);
        } catch (MarketplaceException e) {
            // reconnectRequired 已在 withAccessToken 里置位——作为状态返回而非 400（浏览面不报错）
            return new EntitlementsSnapshot(true, true, List.of());
        }
    }

    /**
     * 刷新令牌（串行化 + 双检）：{@code staleAccessToken} 是触发刷新的旧访问令牌——拿到锁后
     * 重读持久化连接，若已被别的请求轮换（访问令牌变了）直接复用；否则调用刷新端点，
     * <b>新令牌对先落库</b>（refresh token 单次有效）再返回。
     */
    private String refreshTokens(String staleAccessToken) {
        refreshLock.lock();
        try {
            MarketplaceConnection connection = store.load();
            if (connection == null) {
                throw new MarketplaceException("marketplace.error.notConnected",
                        "No marketplace account connected");
            }
            if (connection.reconnectRequired()) {
                throw reconnectRequired();
            }
            if (!connection.accessToken().equals(staleAccessToken) && !isExpired(connection)) {
                return connection.accessToken();
            }
            OAuthTokens tokens;
            try {
                tokens = client.refreshAccessToken(connection.refreshToken());
            } catch (MarketplaceOAuthException e) {
                // invalid_grant：refresh token 已死（远程吊销/轮换竞态）——终局，须重连
                logger.warn("Marketplace token refresh rejected ({}); reconnect required", e.getError());
                store.save(connection.asReconnectRequired());
                throw reconnectRequired();
            }
            long expiresAt = currentTimeMillis.getAsLong() / 1000 + tokens.expiresIn();
            String rotatedRefresh = tokens.refreshToken() != null ? tokens.refreshToken() : connection.refreshToken();
            MarketplaceConnection updated = connection.withTokens(tokens.accessToken(), expiresAt, rotatedRefresh);
            store.save(updated);
            return updated.accessToken();
        } finally {
            refreshLock.unlock();
        }
    }

    private void markReconnectRequired() {
        MarketplaceConnection connection = store.load();
        if (connection != null && !connection.reconnectRequired()) {
            store.save(connection.asReconnectRequired());
        }
    }

    private boolean isExpired(MarketplaceConnection connection) {
        return connection.accessTokenExpiresAt() - EXPIRY_SKEW_SECONDS
                <= currentTimeMillis.getAsLong() / 1000;
    }

    private static MarketplaceException reconnectRequired() {
        return new MarketplaceException("marketplace.error.reconnectRequired",
                "Marketplace connection is no longer valid; reconnect the account");
    }

    // ----- 类型 --------------------------------------------------------------

    /** 挂起的设备授权流（内存态；device_code 是凭据，绝不出服务端） */
    private static final class PendingAuth {
        private final String deviceCode;
        private final long expiresAtMillis;
        private volatile long intervalSeconds;
        private volatile long nextPollAtMillis;

        private PendingAuth(String deviceCode, long expiresAtMillis, long intervalSeconds) {
            this.deviceCode = deviceCode;
            this.expiresAtMillis = expiresAtMillis;
            this.intervalSeconds = intervalSeconds;
        }
    }

    /** 设备授权流启动结果（控制台连接对话框的数据源；不含 device_code） */
    public record DeviceStart(String userCode, String verificationUri, String verificationUriComplete,
                              int expiresIn, int interval) {
    }

    /** 轮询状态 */
    public enum PollStatus {
        /** 等待用户批准（或未到轮询间隔/市场瞬时不可达） */
        PENDING,
        /** 已连接（连接已持久化） */
        CONNECTED,
        /** 用户拒绝了本次授权 */
        DENIED,
        /** 设备码过期或已失效，须重新发起 */
        EXPIRED,
        /** 没有挂起的授权流（如后端重启）——按过期处理重新发起 */
        NONE
    }

    /** 轮询结果（CONNECTED 时携带账号展示信息） */
    public record PollResult(PollStatus status, @Nullable String accountEmail, @Nullable String accountName) {
        static PollResult of(PollStatus status) {
            return new PollResult(status, null, null);
        }
    }

    /** 断开结果：{@code remoteRevoked}=false 表示远程吊销未成（本地已断开） */
    public record DisconnectResult(boolean remoteRevoked) {
    }

    /** 连接状态投影（不含令牌） */
    public record ConnectionStatus(boolean connected, boolean reconnectRequired,
                                   @Nullable String accountEmail, @Nullable String accountName,
                                   @Nullable String instanceName, @Nullable String connectedAt) {
    }

    /** 授权快照：{@code connected}/{@code reconnectRequired} + 有效授权条目（slug 列表的数据源） */
    public record EntitlementsSnapshot(boolean connected, boolean reconnectRequired,
                                       List<Entitlement> entitlements) {
    }
}
