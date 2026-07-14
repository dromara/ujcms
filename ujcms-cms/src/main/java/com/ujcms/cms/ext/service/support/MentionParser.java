package com.ujcms.cms.ext.service.support;

import com.ujcms.cms.core.domain.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评论 @提及 解析（纯静态工具类，见 docs/spec/membership/comment-mention.md）
 * <p>
 * 用户名字符集固定采用默认注册配置 {@code Config.Register.usernameRegex}（{@code ^[0-9a-zA-Z一-龥.\-_]+$}）：
 * 解析规则必须稳定，不能跟随站点自定义配置漂移。自定义放宽用户名字符集的站点仍按默认字符集解析（已知限制）
 *
 * @author PONY
 */
public final class MentionParser {
    /**
     * 每条评论最多解析的提及用户数
     */
    public static final int MENTION_LIMIT = 5;

    /**
     * 提及标记：{@code @} 后跟 1-30 个用户名字符（30 为 username 列长度上限，贪婪最长匹配，超长整体放弃而非截断）。
     * {@code @} 须位于串首或前一字符在字符集之外（空白、标点、中文标点），邮箱地址因 {@code @} 前是字母而自然忽略
     */
    private static final Pattern MENTION_PATTERN = Pattern.compile(
            "(?<![0-9A-Za-z\\u4e00-\\u9fa5._-])@([0-9A-Za-z\\u4e00-\\u9fa5._-]{1,30})(?![0-9A-Za-z\\u4e00-\\u9fa5._-])");

    /**
     * 解析文本中的 @提及 并解析为用户ID。按用户ID去重、保持首次出现顺序，最多 {@link #MENTION_LIMIT} 个。
     * 候选名未命中时去除末尾的 {@code [._-]+} 重试一次（如句尾的 {@code @pony.}），仍未命中则静默忽略
     *
     * @param text     评论文本
     * @param resolver 按用户名精确查找用户（通常为 {@code userService::selectByUsername}），未找到返回 {@code null}
     * @return 被提及的用户ID，按首次出现顺序
     */
    public static List<Long> parseUserIds(@Nullable String text, Function<String, User> resolver) {
        if (text == null || text.indexOf('@') < 0) {
            return Collections.emptyList();
        }
        List<Long> userIds = new ArrayList<>(MENTION_LIMIT);
        // 已查找过的候选名（含重试名），避免重复查库；正确性由 userIds 按ID去重保证
        Set<String> attemptedNames = new HashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(text);
        while (userIds.size() < MENTION_LIMIT && matcher.find()) {
            String name = matcher.group(1);
            if (!attemptedNames.add(name)) {
                continue;
            }
            User user = resolver.apply(name);
            if (user == null) {
                // . - _ 也是合法用户名字符，末尾的可能是句尾标点：去除后重试一次
                String stripped = StringUtils.stripEnd(name, "._-");
                if (stripped.isEmpty() || !attemptedNames.add(stripped)) {
                    continue;
                }
                user = resolver.apply(stripped);
                if (user == null) {
                    continue;
                }
            }
            if (!userIds.contains(user.getId())) {
                userIds.add(user.getId());
            }
        }
        return userIds;
    }

    private MentionParser() {
    }
}
