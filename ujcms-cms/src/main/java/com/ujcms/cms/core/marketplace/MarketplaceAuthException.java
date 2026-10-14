package com.ujcms.cms.core.marketplace;

/**
 * 携带 Bearer 令牌的实例 API 调用被拒（{@code /me} / {@code /me/entitlements} / 下载授权）。
 * <ul>
 * <li>{@link Kind#UNAUTHORIZED}（401）：令牌无效或已过期——市场的 JWT 签名密钥在内存中，
 * 服务端重启即作废所有在途访问令牌，本地的过期时间戳不可尽信；调用方应强制刷新后重试一次。</li>
 * <li>{@link Kind#INSUFFICIENT_SCOPE}（403 problem+json {@code code=insufficient_scope}）：
 * 令牌缺少所需 scope——连接是旧授权（scope 集合变更），刷新救不回来，须重新连接账号。</li>
 * </ul>
 */
public class MarketplaceAuthException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** 被拒类别 */
    public enum Kind {
        /** 401：令牌无效/过期（可刷新后重试） */
        UNAUTHORIZED,
        /** 403 insufficient_scope：授权范围不足（须重新连接） */
        INSUFFICIENT_SCOPE
    }

    private final Kind kind;

    public MarketplaceAuthException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
