package com.ujcms.cms.core.component;

import com.github.promeg.pinyinhelper.Pinyin;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.mapper.SiteMapper;
import com.ujcms.common.text.Slugs;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

/**
 * 别名生成器（docs/spec/permalinks/plan.md D4）。服务端在别名留空时生成站内唯一别名：
 * {@code slugify(标题)} → 站点开启“中文转拼音”时对标题做拼音转写再 slugify → 回退为 ID 字符串 →
 * 冲突时追加 {@code -2}、{@code -3}… 。纯离线、确定性、不做网络调用；AI 生成别名只在控制台按钮显式触发。
 *
 * @author PONY
 */
@Component
public class SlugGenerator {
    /**
     * 文章别名长度上限（ujcms_article.slug_）
     */
    public static final int ARTICLE_MAX_LENGTH = 160;
    /**
     * 栏目别名长度上限（ujcms_channel.slug_）
     */
    public static final int CHANNEL_MAX_LENGTH = 50;
    /**
     * 专题别名长度上限（ujcms_topic.slug_）
     */
    public static final int TOPIC_MAX_LENGTH = 50;
    /**
     * 拼音回退最多取用的音节数，超出部分丢弃（20 字中文标题的全拼约 80 字符，过长无意义）
     */
    public static final int PINYIN_MAX_SYLLABLES = 12;

    private final SiteMapper siteMapper;

    public SlugGenerator(SiteMapper siteMapper) {
        this.siteMapper = siteMapper;
    }

    /**
     * 生成站内唯一别名。先锁住站点行（docs/spec/article-data-permission/plan.md D22）：两个请求同时为同名实体生成别名时，
     * 双方都查得别名空闲、都写同一个，后写的撞上唯一键。锁住站点行后，本站生成别名的写入依次进行；{@code exists}
     * 须是锁定读（{@code for update}），读到的才是最新提交的行而不是本事务的快照。须在调用方已开启的事务里执行，
     * 锁随写入一起提交
     *
     * @param siteId    站点ID（据此读取“中文转拼音”设置；仅在 slugify 结果为空时才查询）
     * @param text      标题/名称
     * @param id        实体ID（回退值）
     * @param maxLength 长度上限
     * @param exists    别名是否已被站内其他实体占用（调用方负责排除自身），应为锁定读
     */
    public String generate(Long siteId, @Nullable String text, long id, int maxLength, Predicate<String> exists) {
        siteMapper.lockById(siteId);
        String slug = suggest(siteId, text, maxLength, exists);
        return slug.isEmpty() ? String.valueOf(id) : slug;
    }

    /**
     * 别名建议：与 {@link #generate} 同规则，但无可用字符时返回空串而非 ID（供控制台“留空自动生成”提示）
     */
    public String suggest(Long siteId, @Nullable String text, int maxLength, Predicate<String> exists) {
        String base = base(siteId, text, maxLength);
        if (base.isEmpty()) {
            return "";
        }
        return unique(base, maxLength, exists);
    }

    /**
     * 别名的生成基础：{@code slugify(标题)}，为空且站点开启“中文转拼音”时取拼音转写。无可用字符时返回空串（调用方回退为 ID）
     */
    public String base(Long siteId, @Nullable String text, int maxLength) {
        String base = Slugs.slugify(text, maxLength);
        if (base.isEmpty() && text != null && isPinyinEnabled(siteId)) {
            base = Slugs.slugify(toPinyin(text, PINYIN_MAX_SYLLABLES), maxLength);
        }
        return base;
    }


    /**
     * 冲突消解：{@code base}、{@code base-2}、{@code base-3}…（后缀占位从上限中扣除）
     */
    public String unique(String base, int maxLength, Predicate<String> exists) {
        String candidate = base;
        for (int i = 2; exists.test(candidate); i++) {
            String suffix = "-" + i;
            candidate = Slugs.truncate(base, maxLength - suffix.length()) + suffix;
        }
        return candidate;
    }

    /**
     * 汉字逐字转全拼（无声调，音节间以空格分隔，非汉字原样保留），最多 {@code maxSyllables} 个音节。
     * 多音字取字典首选读音——URL 别名不追求读音精确，只求可读、稳定。
     */
    public static String toPinyin(String text, int maxSyllables) {
        StringBuilder sb = new StringBuilder(text.length() * 5);
        int syllables = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Pinyin.isChinese(c)) {
                if (syllables >= maxSyllables) {
                    break;
                }
                sb.append(' ').append(Pinyin.toPinyin(c)).append(' ');
                syllables++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private boolean isPinyinEnabled(Long siteId) {
        Site site = siteMapper.select(siteId);
        return site != null && site.getPermalink().isPinyin();
    }
}
