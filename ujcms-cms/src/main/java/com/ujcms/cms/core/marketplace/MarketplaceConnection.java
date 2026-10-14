package com.ujcms.cms.core.marketplace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.jspecify.annotations.Nullable;

/**
 * 应用市场账号连接（remote-marketplace C2 / D-C1-1）：部署级唯一，序列化为 JSON 存于
 * {@code ujcms_config.marketplace_settings_}（整列 SM4 加密，config-secrets 约定）。
 * 令牌永不出服务端（D-K3）——本对象只在后端流转，绝不进任何 API 响应
 * （控制台只见 {@code accountEmail} 等展示字段的投影）。
 *
 * <p>{@code refreshToken} 单次有效（服务端每次刷新轮换）：新令牌对必须先落库、再作废旧令牌。
 * {@code reconnectRequired} 在刷新遭 {@code invalid_grant} 或资源调用遭
 * {@code insufficient_scope} 时置位——连接已救不回来，操作员须重新连接。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketplaceConnection(
        String accessToken,
        /* 访问令牌过期时刻（epoch 秒；服务端重启会提前作废在途 JWT，过期判断仅是首道闸门） */
        long accessTokenExpiresAt,
        String refreshToken,
        @Nullable String scope,
        @Nullable String accountEmail,
        @Nullable String accountName,
        @Nullable String instanceId,
        @Nullable String instanceName,
        /* 连接建立时刻（ISO-8601） */
        @Nullable String connectedAt,
        boolean reconnectRequired) {

    /** 轮换令牌对（其余连接信息不变） */
    public MarketplaceConnection withTokens(String accessToken, long accessTokenExpiresAt, String refreshToken) {
        return new MarketplaceConnection(accessToken, accessTokenExpiresAt, refreshToken, scope,
                accountEmail, accountName, instanceId, instanceName, connectedAt, false);
    }

    /** 标记连接失效（保留账号展示信息供控制台提示「重新连接」） */
    public MarketplaceConnection asReconnectRequired() {
        return new MarketplaceConnection(accessToken, accessTokenExpiresAt, refreshToken, scope,
                accountEmail, accountName, instanceId, instanceName, connectedAt, true);
    }
}
