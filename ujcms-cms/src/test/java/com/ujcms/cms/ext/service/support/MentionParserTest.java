package com.ujcms.cms.ext.service.support;

import com.ujcms.cms.core.domain.User;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MentionParser 单元测试（docs/spec/membership/comment-mention.md §10.1）
 */
class MentionParserTest {

    private final Map<String, User> users = new HashMap<>();

    private void addUser(long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        users.put(username, user);
    }

    private Function<String, User> resolver() {
        return users::get;
    }

    private List<Long> parse(String text) {
        return MentionParser.parseUserIds(text, resolver());
    }

    MentionParserTest() {
        addUser(1L, "pony");
        addUser(2L, "tony");
        addUser(3L, "a.b-c_");
        addUser(4L, "名字");
    }

    @Test
    void emailIgnored() {
        assertTrue(parse("联系 foo@pony.com 获取详情").isEmpty());
        // 邮箱后真正的提及仍可解析
        assertEquals(List.of(2L), parse("联系 foo@pony.com 或 @tony"));
    }

    @Test
    void cjkTrailingPunctuationOutsideCharsetIsBoundary() {
        // 。在字符集之外，直接终止匹配，无需重试
        assertEquals(List.of(4L), parse("@名字。你好"));
    }

    @Test
    void trailingStripRetry() {
        // 句尾的 . 是合法用户名字符，贪婪匹配为 "pony."，未命中后去除末尾 [._-]+ 重试
        assertEquals(List.of(1L), parse("谢谢 @pony."));
        assertEquals(List.of(1L), parse("谢谢 @pony.-_"));
        // 全部由 [._-] 组成的候选名，去除后为空，忽略
        assertTrue(parse("看这里 @...").isEmpty());
    }

    @Test
    void punctuationCharsInsideUsername() {
        assertEquals(List.of(3L), parse("你好 @a.b-c_ 再见"));
    }

    @Test
    void cappedAtLimit() {
        addUser(5L, "u5");
        addUser(6L, "u6");
        addUser(7L, "u7");
        List<Long> ids = parse("@pony @tony @a.b-c_ @u5 @u6 @u7");
        assertEquals(MentionParser.MENTION_LIMIT, ids.size());
        assertEquals(List.of(1L, 2L, 3L, 5L, 6L), ids);
    }

    @Test
    void unknownNamesDoNotCountTowardLimit() {
        addUser(5L, "u5");
        addUser(6L, "u6");
        List<Long> ids = parse("@x1 @x2 @pony @tony @a.b-c_ @u5 @u6");
        assertEquals(List.of(1L, 2L, 3L, 5L, 6L), ids);
    }

    @Test
    void nameLongerThan30CharsRejected() {
        String name30 = "a".repeat(30);
        addUser(9L, name30);
        assertEquals(List.of(9L), parse("@" + name30));
        // 31 字符的连续串整体放弃，不截断为前 30 字符
        assertTrue(parse("@" + name30 + "a").isEmpty());
    }

    @Test
    void boundaryCases() {
        // 行首
        assertEquals(List.of(1L), parse("@pony 你好"));
        // 括号等字符集外的字符是合法边界
        assertEquals(List.of(1L), parse("(@pony)"));
        assertEquals(List.of(1L), parse("你好，@pony"));
        assertEquals(List.of(1L), parse("第一行\n@pony"));
        // 汉字属于用户名字符集，紧邻的 @ 不构成提及（与邮箱规则一致）
        assertTrue(parse("你好@pony").isEmpty());
        // . 也在字符集内，同样不构成边界
        assertTrue(parse("end.@pony").isEmpty());
        // 连续 @：第二个 @ 前是字符集外的 @，可解析
        assertEquals(List.of(1L), parse("@@pony"));
    }

    @Test
    void dedupeByUserIdFirstOccurrenceOrder() {
        assertEquals(List.of(1L, 2L), parse("@pony @tony @pony"));
        // "pony." 经重试与 "pony" 解析为同一用户，按ID去重
        assertEquals(List.of(1L), parse("@pony. 和 @pony"));
    }

    @Test
    void nullOrPlainTextReturnsEmpty() {
        assertTrue(MentionParser.parseUserIds(null, resolver()).isEmpty());
        assertTrue(parse("没有提及的普通评论").isEmpty());
        assertTrue(parse("@ 后面是空格").isEmpty());
    }

    @Test
    void repeatedNamesLookedUpOnce() {
        AtomicInteger lookups = new AtomicInteger();
        Function<String, User> counting = name -> {
            lookups.incrementAndGet();
            return users.get(name);
        };
        MentionParser.parseUserIds("@pony @pony @unknown @unknown", counting);
        // pony 1 次；unknown 1 次 + 重试 0 次（无尾部 [._-] 可去除）
        assertEquals(2, lookups.get());
    }
}
