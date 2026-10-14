package com.ujcms.cms.core.component;

import com.ujcms.cms.core.support.Props;
import com.ujcms.common.ip.IpAddresses;
import org.springframework.stereotype.Service;

/**
 * 匿名互动 IP 限流 Service。
 * <p>
 * 限制单个 IP 在时间窗口内的互动（投票/调查/文章顶踩）次数，防止脚本批量刷量；与按 Cookie/IP/会员
 * 的去重（一人一票）是不同职责——去重控制“计入几票”，限流控制“请求速率”。
 * <p>
 * 默认关闭（{@code maxPerWindow <= 0}）。注意：IP 限流在 CGNAT / 共享出口（运营商、校园网等）环境下
 * 可能误伤大量正常用户，需酌情调大阈值或保持关闭。
 * <p>
 * 计数键由 {@link IpAddresses#throttleKey(String, int)} 派生：IPv6 按前缀（{@code ujcms.ipv6-throttle-prefix-length}，
 * 默认 /64）分组，避免隐私扩展地址轮换绕过限流；共享同一 /64 的用户同样共享配额。
 *
 * @author PONY
 */
@Service
public class IpInteractionCounterService {
    private final IpInteractionCache cache;
    private final Props props;

    public IpInteractionCounterService(IpInteractionCache cache, Props props) {
        this.cache = cache;
        this.props = props;
    }

    private String windowKey(String ip) {
        long window = System.currentTimeMillis() / 1000 / IpInteractionCache.WINDOW_SECONDS;
        // 分隔符不用冒号，避免与 IPv6 键混淆
        return IpAddresses.throttleKey(ip, props.getIpv6ThrottlePrefixLength()) + '|' + window;
    }

    /**
     * 当前 IP 在本时间窗口内的互动次数是否已达上限。
     *
     * @param maxPerWindow 每个时间窗口允许的最大互动次数，{@code <= 0} 表示不限流
     */
    public boolean isExcessive(String ip, int maxPerWindow) {
        return maxPerWindow > 0 && cache.getCount(windowKey(ip)) >= maxPerWindow;
    }

    /**
     * 记录一次成功互动（仅在真正计入互动时调用，重复/已操作不计数）。
     */
    public void updateCount(String ip) {
        String key = windowKey(ip);
        cache.updateCount(key, cache.getCount(key) + 1);
    }
}
