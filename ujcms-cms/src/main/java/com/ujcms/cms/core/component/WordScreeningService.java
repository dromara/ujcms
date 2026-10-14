package com.ujcms.cms.core.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ujcms.cms.core.domain.ArticleExt;
import com.ujcms.cms.core.domain.ErrorWord;
import com.ujcms.cms.core.domain.SensitiveWord;
import com.ujcms.cms.core.service.ErrorWordService;
import com.ujcms.cms.core.service.SensitiveWordService;
import com.ujcms.cms.core.service.args.ErrorWordArgs;
import com.ujcms.cms.core.service.args.SensitiveWordArgs;
import com.ujcms.common.web.HtmlParserUtils;

/**
 * 词库筛查引擎（docs/spec/ai/screening.md §3）。以 Aho-Corasick 自动机在一次扫描中匹配
 * 敏感词与易错词两个词库的全部启用词条，替代逐词 {@code contains()} 的线性扫描。
 * <p>
 * 敏感词不区分大小写（改写大小写即可绕过的词库没有意义）；易错词精确匹配（大小写本身常是纠错内容，
 * 如 Wechat → WeChat，不区分大小写会让修正后的文本再次命中）。故两个词库各建一棵自动机，同属一个快照。
 * HTML 文本由调用方先行去除标签（docs/spec/word-dictionaries/plan.md D3）。
 * 自动机首次使用时构建；词库变更由 {@link SensitiveWordService}/{@link ErrorWordService}
 * 调用 {@link #refresh()} 标脏，下次调用时重建（volatile + 脏标记热刷新）。
 *
 * @author PONY
 */
@Service
public class WordScreeningService {
    private static final Logger logger = LoggerFactory.getLogger(WordScreeningService.class);

    /**
     * 快照最大年龄。脏标记为 JVM 本地，集群下其他节点感知不到词库变更，
     * 到龄重建作为收敛兜底（与 Spring 缓存 expireAfterWrite 的角色一致）
     */
    private static final long MAX_AGE_MILLIS = 5 * 60 * 1000L;

    private final SensitiveWordService sensitiveWordService;
    private final ErrorWordService errorWordService;

    /**
     * 词库快照。为 {@code null} 表示尚未构建
     */
    @Nullable
    private volatile Snapshot snapshot;
    /**
     * 脏标记。词库变更后置位，下次筛查时重建快照
     */
    private volatile boolean dirty = true;
    /**
     * 当前快照的构建时间（毫秒时间戳）
     */
    private volatile long builtAt;

    /**
     * 词库服务须回调本类的 {@link #refresh()}，故此处懒注入以断开循环依赖
     */
    public WordScreeningService(@Lazy SensitiveWordService sensitiveWordService,
                                @Lazy ErrorWordService errorWordService) {
        this.sensitiveWordService = sensitiveWordService;
        this.errorWordService = errorWordService;
    }

    /**
     * 筛查文本，报告全部命中（含重叠命中），按起始位置排序
     *
     * @param text 待筛查文本
     * @return 命中列表。偏移量为 {@code text} 中的字符位置，{@code end} 为开区间
     */
    public List<WordHit> screen(@Nullable String text) {
        if (StringUtils.isEmpty(text)) {
            return Collections.emptyList();
        }
        Snapshot current = getSnapshot();
        List<WordHit> hits = new ArrayList<>();
        // Emit 的 end 为闭区间，转为开区间
        if (current.sensitive != null) {
            for (Emit emit : current.sensitive.parseText(text)) {
                hits.add(new WordHit(emit.getKeyword(), WordType.SENSITIVE, emit.getStart(), emit.getEnd() + 1, null));
            }
        }
        if (current.error != null) {
            for (Emit emit : current.error.parseText(text)) {
                String correct = current.corrections.get(emit.getKeyword());
                if (!alreadyCorrect(text, emit.getStart(), emit.getKeyword(), correct)) {
                    hits.add(new WordHit(emit.getKeyword(), WordType.ERROR, emit.getStart(), emit.getEnd() + 1, correct));
                }
            }
        }
        hits.sort(Comparator.comparingInt(WordHit::start).thenComparingInt(WordHit::end));
        return hits;
    }

    /**
     * 文章的筛查范围：字段名 → 纯文本，按页面上的先后排列。保存拦截与网站普查共用，两处看到的字段与文本形态才一致
     * （docs/spec/word-dictionaries/plan.md D4）。标题类字段原样筛查，正文先去除标签；自定义字段不在筛查范围
     *
     * @param ext 文章扩展对象
     * @return 非空字段的纯文本
     */
    /**
     * 文章各筛查字段里的第一个敏感词；没有则为 {@code null}。只看敏感词——易错词多与语境有关，不拦保存
     * （docs/spec/word-dictionaries/plan.md D1）。控制台保存与批量导入共用这一条规则
     */
    @Nullable
    public String firstSensitiveWord(ArticleExt ext) {
        for (String text : articleFields(ext).values()) {
            for (WordHit hit : screen(text)) {
                if (hit.type() == WordType.SENSITIVE) {
                    return hit.word();
                }
            }
        }
        return null;
    }

    public static Map<String, String> articleFields(ArticleExt ext) {
        Map<String, String> fields = new LinkedHashMap<>(8);
        putField(fields, FIELD_TITLE, ext.getTitle());
        putField(fields, FIELD_SUBTITLE, ext.getSubtitle());
        putField(fields, FIELD_FULL_TITLE, ext.getFullTitle());
        putField(fields, FIELD_SEO_DESCRIPTION, ext.getSeoDescription());
        putField(fields, FIELD_TEXT, HtmlParserUtils.getText(ext.getText()));
        return fields;
    }

    private static void putField(Map<String, String> fields, String name, @Nullable String text) {
        if (StringUtils.isNotBlank(text)) {
            fields.put(name, text);
        }
    }

    public static final String FIELD_TITLE = "title";
    public static final String FIELD_SUBTITLE = "subtitle";
    public static final String FIELD_FULL_TITLE = "fullTitle";
    public static final String FIELD_SEO_DESCRIPTION = "seoDescription";
    public static final String FIELD_TEXT = "text";

    /**
     * 该处文本是否已是正确写法。正确词包含易错词本身时（如 台湾 → 中国台湾），已写对的文本仍会被易错词命中：
     * 不排除则修正后的文本永远提示，“全部修正”还会把 中国台湾 改成 中国中国台湾。
     * 易错词可能在正确词中出现多次，逐个位置比对
     */
    private static boolean alreadyCorrect(String text, int start, String name, @Nullable String correct) {
        if (correct == null) {
            return false;
        }
        for (int index = correct.indexOf(name); index >= 0; index = correct.indexOf(name, index + 1)) {
            int from = start - index;
            if (from >= 0 && text.startsWith(correct, from)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 词条规范化：连续空白（含不换行空格、全角空格）折叠为一个空格，并去除首尾空白。
     * 与筛查文本的形态一致（{@code HtmlParserUtils.getText} 同样折叠空白），否则含连续空格或换行的词条永远匹配不上
     */
    public static String normalizeWord(@Nullable String word) {
        return word == null ? "" : WHITESPACE.matcher(word).replaceAll(" ").trim();
    }

    private static final Pattern WHITESPACE = Pattern.compile("[\\s\u00A0\u3000]+");

    /**
     * 大小写折叠，与自动机的不区分大小写规则一致（逐字符 {@link Character#toLowerCase(char)}，不改变长度）。
     * 敏感词的重复判定须用同一规则，否则“词库认为不重复、引擎认为是同一个词”
     */
    public static String foldCase(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = Character.toLowerCase(chars[i]);
        }
        return new String(chars);
    }

    /**
     * 遮蔽敏感词（仅 {@link WordType#SENSITIVE}，易错词不遮蔽）。
     * 基础能力，本波次不接线（遮蔽已发布文本须经人工确认，见 screening.md §7.6）
     *
     * @param text     原文本
     * @param maskChar 遮蔽字符
     * @return 遮蔽后的文本
     */
    public String mask(@Nullable String text, char maskChar) {
        if (StringUtils.isEmpty(text)) {
            return StringUtils.defaultString(text);
        }
        char[] chars = text.toCharArray();
        for (WordHit hit : screen(text)) {
            if (hit.type() == WordType.SENSITIVE) {
                for (int i = hit.start(); i < hit.end(); i++) {
                    chars[i] = maskChar;
                }
            }
        }
        return new String(chars);
    }

    /**
     * 标记词库已变更。仅置脏标记，重建推迟到下次筛查。
     * 事务内调用时推迟到提交后置位：过早置位会让并发重建读到未提交数据并消耗掉脏标记，
     * 变更从此丢失（回滚则不置位）
     */
    public void refresh() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dirty = true;
                }
            });
        } else {
            dirty = true;
        }
    }

    private Snapshot getSnapshot() {
        Snapshot current = snapshot;
        if (current != null && !dirty && System.currentTimeMillis() - builtAt < MAX_AGE_MILLIS) {
            return current;
        }
        synchronized (this) {
            current = snapshot;
            if (current != null && !dirty && System.currentTimeMillis() - builtAt < MAX_AGE_MILLIS) {
                return current;
            }
            // 先清脏标记再构建：构建期间的并发 refresh() 会重新置位，下次调用再次重建
            dirty = false;
            try {
                current = build();
            } catch (RuntimeException e) {
                // 构建失败（如数据库瞬时故障）：恢复脏标记待下次重试。有旧快照则降级续用，否则上抛
                dirty = true;
                Snapshot stale = snapshot;
                if (stale != null) {
                    logger.error("Word screening snapshot rebuild failed, serving stale snapshot", e);
                    return stale;
                }
                throw e;
            }
            snapshot = current;
            builtAt = System.currentTimeMillis();
            return current;
        }
    }

    private Snapshot build() {
        // 空白词条一律跳过：单个空格的词条会命中几乎所有文本
        Map<String, String> sensitive = new LinkedHashMap<>(256);
        for (SensitiveWord word : sensitiveWordService.selectList(SensitiveWordArgs.of().enabled(true))) {
            if (StringUtils.isNotBlank(word.getName())) {
                // 仅大小写不同的词条在自动机里是同一个词，只留一条，否则同一位置重复命中
                sensitive.putIfAbsent(foldCase(word.getName()), word.getName());
            }
        }
        Map<String, String> corrections = new HashMap<>(256);
        Set<String> error = new LinkedHashSet<>(256);
        for (ErrorWord word : errorWordService.selectList(ErrorWordArgs.of().enabled(true))) {
            // 重名词条以先读到的（最新一条）为准
            if (StringUtils.isNotBlank(word.getName()) && error.add(word.getName())) {
                if (StringUtils.isNotBlank(word.getCorrect())) {
                    corrections.put(word.getName(), word.getCorrect());
                }
            }
        }
        // 不忽略重叠命中（ignoreOverlaps 关闭）：报告全部命中
        Trie sensitiveTrie = sensitive.isEmpty() ? null
                : Trie.builder().ignoreCase().addKeywords(sensitive.values()).build();
        Trie errorTrie = error.isEmpty() ? null : Trie.builder().addKeywords(error).build();
        return new Snapshot(sensitiveTrie, errorTrie, corrections);
    }

    /**
     * 词库快照。两棵自动机与修正词一并替换，保证筛查读到一致的版本
     *
     * @param sensitive   敏感词自动机（不区分大小写）。词库为空时为 {@code null}
     * @param error       易错词自动机（精确匹配）。词库为空时为 {@code null}
     * @param corrections 易错词 → 正确词
     */
    private record Snapshot(@Nullable Trie sensitive, @Nullable Trie error, Map<String, String> corrections) {
    }

    /**
     * 词条类型
     */
    public enum WordType {
        /**
         * 敏感词
         */
        SENSITIVE,
        /**
         * 易错词
         */
        ERROR
    }

    /**
     * 命中记录
     *
     * @param word    命中的词条（词库中的写法）。易错词有 {@code text.substring(start, end).equals(word)}；
     *                敏感词不区分大小写，原文写法以 {@code text.substring(start, end)} 为准
     * @param type    词条类型
     * @param start   起始字符偏移（含）
     * @param end     结束字符偏移（不含）
     * @param correct 正确词。仅易错词有
     */
    public record WordHit(String word, WordType type, int start, int end, @Nullable String correct) {
        public WordHit(String word, WordType type, int start, int end) {
            this(word, type, start, end, null);
        }
    }
}
