package com.ujcms.cms.core.composition;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * 确定性 originId 铸造（theme-seed-export §4.1）。为第 3/4 类行（操作员行 / 他源行）铸造
 * 主题内稳定ID：<b>纯函数，不写库</b>（D2；adopt 回写属 E2）。行内容不变则连续导出铸造结果
 * 稳定（站内一致性）；导出→擦除→导入后行携带三元组（第 1 类），originId 自库行保留——
 * 跨往返稳定由此保证。
 * <ul>
 *   <li>栏目/专题：{@code originId = alias}（导入侧的跨引用键本就缺省如此）；别名空缺时
 *       回退内容散列形（{@code c-}/{@code t-} + 8 位十六进制）。</li>
 *   <li>文章：{@code a-} + 文章别名（有则用）；否则标题 slug（CJK 安全：无拼音转写——
 *       slug 为空即回退与ID无关的内容散列 {@code a-<8位十六进制>}）。</li>
 *   <li>冲突以 {@code -2}、{@code -3}… 后缀消解（调用方保证遍历序确定）。</li>
 * </ul>
 *
 * @author PONY
 */
final class SeedOriginIds {

    /**
     * originId 架构上限 128；留出冲突后缀余量
     */
    private static final int MAX_BASE_LENGTH = 100;

    private SeedOriginIds() {
    }

    /**
     * 栏目/专题的 originId 铸造。
     *
     * @param alias       行别名（空白回退散列形）
     * @param prefix      散列回退前缀（{@code c-} / {@code t-}）
     * @param contentHash 与ID无关的内容散列（{@code SeedHash.channelHash}/{@code topicHash}）
     * @param used        已占用的 originId（本类别；调用方先登记全部保留ID再铸造）
     */
    static String mintAliased(@Nullable String alias, String prefix, String contentHash, Set<String> used) {
        String base = alias != null && !alias.isBlank() ? truncate(alias) : prefix + hash8(contentHash);
        return unique(base, used);
    }

    /**
     * 文章的 originId 铸造（§4.1）。
     *
     * @param alias       文章别名（有则优先）
     * @param title       标题（slug 源）
     * @param contentHash 与ID无关的内容散列（{@code SeedHash.articleHash}）
     * @param used        已占用的 originId
     */
    static String mintArticle(@Nullable String alias, String title, String contentHash, Set<String> used) {
        String base;
        if (alias != null && !alias.isBlank()) {
            base = "a-" + truncate(alias);
        } else {
            String slug = slug(title);
            base = slug.isEmpty() ? "a-" + hash8(contentHash) : "a-" + truncate(slug);
        }
        return unique(base, used);
    }

    /**
     * 投票的 originId 铸造（E4b / D29——投票无别名，标题 slug 或散列回退，文章同法）。
     *
     * @param title       标题（slug 源）
     * @param contentHash 与ID无关的内容散列（{@code SeedHash.voteHash}）
     * @param used        已占用的 originId
     */
    static String mintVote(String title, String contentHash, Set<String> used) {
        String slug = slug(title);
        String base = slug.isEmpty() ? "v-" + hash8(contentHash) : "v-" + truncate(slug);
        return unique(base, used);
    }

    /**
     * 问卷的 originId 铸造（E4d / D35——问卷无别名，标题 slug 或散列回退，投票同法）。
     *
     * @param title       标题（slug 源）
     * @param contentHash 与ID无关的内容散列（{@code SeedHash.surveyHash}）
     * @param used        已占用的 originId
     */
    static String mintSurvey(String title, String contentHash, Set<String> used) {
        String slug = slug(title);
        String base = slug.isEmpty() ? "s-" + hash8(contentHash) : "s-" + truncate(slug);
        return unique(base, used);
    }

    /**
     * 表单类型的 originId 铸造（E4e / D40——表单无别名，名称 slug 或散列回退，问卷同法）。
     *
     * @param name        名称（slug 源；CJK 走散列回退）
     * @param contentHash 与ID无关的内容散列（{@code SeedHash.formTypeHash}）
     * @param used        已占用的 originId
     */
    static String mintFormType(String name, String contentHash, Set<String> used) {
        String slug = slug(name);
        String base = slug.isEmpty() ? "f-" + hash8(contentHash) : "f-" + truncate(slug);
        return unique(base, used);
    }

    /**
     * 模型的 originId 铸造（model-seeding D7——模型无别名；type 入铸造形消歧同名异类，
     * 名称 slug 或散列回退）。
     *
     * @param type        模型类型（article/channel/topic/blockItem）
     * @param name        模型名称（slug 源；CJK 走散列回退）
     * @param contentHash 与ID无关的内容散列（{@code SeedHash.modelHash}）
     * @param used        已占用的 originId
     */
    static String mintModel(String type, String name, String contentHash, Set<String> used) {
        String slug = slug(name);
        String prefix = "m-" + slug(type) + "-";
        String base = slug.isEmpty() ? prefix + hash8(contentHash) : prefix + truncate(slug);
        return unique(base, used);
    }

    /**
     * 字典类型的 originId 铸造（model-seeding D7——{@code originId = alias}，栏目/区块同法；
     * 别名空缺回退 {@code d-} 散列形）。
     *
     * @param alias       别名（自然键）
     * @param contentHash 与ID无关的内容散列（{@code SeedHash.dictTypeHash}）
     * @param used        已占用的 originId
     */
    static String mintDictType(@Nullable String alias, String contentHash, Set<String> used) {
        return mintAliased(alias, "d-", contentHash, used);
    }

    /**
     * ASCII slug：字母数字段小写连字符相接；CJK 等非 ASCII 丢弃（无拼音转写——不可逆且依赖词典，
     * 空 slug 走散列回退）。
     */
    static String slug(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean pendingDash = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                if (pendingDash && !sb.isEmpty()) {
                    sb.append('-');
                }
                pendingDash = false;
                sb.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                if (pendingDash && !sb.isEmpty()) {
                    sb.append('-');
                }
                pendingDash = false;
                sb.append(Character.toLowerCase(c));
            } else {
                pendingDash = true;
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * 冲突消解：{@code base}、{@code base-2}、{@code base-3}…（登记进 {@code used}）。
     */
    static String unique(String base, Set<String> used) {
        String candidate = base;
        int suffix = 2;
        while (!used.add(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static String hash8(String contentHash) {
        return contentHash.length() >= 8 ? contentHash.substring(0, 8) : contentHash;
    }

    private static String truncate(String value) {
        return value.length() > MAX_BASE_LENGTH ? value.substring(0, MAX_BASE_LENGTH) : value;
    }
}
