package com.ujcms.cms.core.component;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ArticleI18nService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.SiteService;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 语言备选服务（multilingual P3）。FreeMarker 标签（LangSiteList）、SiteMapController 与公开 API
 * 共用的唯一映射大脑，映射规则与 {@code exact} 语义只存在于此一处。
 *
 * <p>映射规则（对语言组内每个对等站点，按 order_, id_ 排序）：
 * <ul>
 * <li>文章页：优先取对等站点的<b>翻译等价集</b>成员（multilingual P5，显式链接）——命中为
 * {@code exact=true} 文章直链；未链接则回退所在栏目起<b>最近的同别名栏目</b>（沿父栏目上溯），
 * 整条链都没有才回退对等首页，{@code exact} 恒为 false（栏目不是该文章的译文）；</li>
 * <li>栏目页：同别名栏目命中为 {@code exact=true}；未命中回退最近的同别名上级栏目、
 * 再不然对等首页，{@code exact=false}；</li>
 * <li>首页：对等首页 {@code exact=true}；</li>
 * <li>其它页（搜索/标签等）：对等首页 {@code exact=false}。</li>
 * </ul>
 * {@code exact} 是 SEO 防火墙：hreflang 只允许输出 {@code exact=true} 条目
 * （Google 要求备选内容对等）。当前站点条目始终包含（{@code current=true, exact=true}，
 * URL 为当前页面自身）——切换器高亮当前项、hreflang 满足自引用要求。
 *
 * @author PONY
 */
@Component
public class LangAlternateService {
    private final SiteService siteService;
    private final ChannelService channelService;
    private final ArticleI18nService articleI18nService;

    public LangAlternateService(SiteService siteService, ChannelService channelService,
                                ArticleI18nService articleI18nService) {
        this.siteService = siteService;
        this.channelService = channelService;
        this.articleI18nService = articleI18nService;
    }

    /**
     * 当前页面上下文的语言备选列表。未分组站点返回仅含自身的单条列表
     *
     * @param site    当前站点
     * @param channel 栏目页上下文。可为 {@code null}
     * @param article 文章页上下文。可为 {@code null}
     * @param isHome  是否首页
     * @return 语言备选列表（组内 order_, id_ 排序；含当前站点条目）
     */
    public List<LangAlternate> listAlternates(Site site, @Nullable Channel channel,
                                              @Nullable Article article, boolean isHome) {
        return listAlternates(site, channel, article, isHome, false);
    }

    /**
     * @param exactOnly 调用方只要 {@code exact} 条目：回退目标无论落在哪都会被过滤，不为它查找
     */
    private List<LangAlternate> listAlternates(Site site, @Nullable Channel channel, @Nullable Article article,
                                               boolean isHome, boolean exactOnly) {
        List<Site> members = siteService.listByLangGroup(site);
        List<LangAlternate> list = new ArrayList<>(members.size());
        for (Site member : members) {
            // 无语言标签无法输出 hreflang，跳过
            if (StringUtils.isBlank(member.getLang())) {
                continue;
            }
            if (member.getId().equals(site.getId())) {
                list.add(currentAlternate(member, channel, article));
            } else {
                list.add(peerAlternate(member, channel, article, isHome, exactOnly));
            }
        }
        return list;
    }

    /**
     * sitemap 专用：仅 {@code exact} 备选（保留 current 条目——hreflang 需要自引用）。
     * {@code channel == null} 视作首页上下文
     *
     * @param site    当前站点
     * @param channel 栏目上下文。为 {@code null} 时视作首页
     * @return exact 语言备选列表
     */
    public List<LangAlternate> listExactAlternates(Site site, @Nullable Channel channel) {
        return listExactAlternates(site, channel, null);
    }

    /**
     * 仅 {@code exact} 备选（含文章上下文；multilingual P5）。
     * {@code channel == null && article == null} 视作首页上下文
     *
     * @param site    当前站点
     * @param channel 栏目上下文。可为 {@code null}
     * @param article 文章上下文。可为 {@code null}
     * @return exact 语言备选列表
     */
    public List<LangAlternate> listExactAlternates(Site site, @Nullable Channel channel, @Nullable Article article) {
        List<LangAlternate> list = new ArrayList<>();
        for (LangAlternate alternate
                : listAlternates(site, channel, article, channel == null && article == null, true)) {
            if (alternate.isExact()) {
                list.add(alternate);
            }
        }
        return list;
    }

    /**
     * sitemap 分页批量（multilingual P5，translation-links.md §5.2）：页内每篇文章的 exact 备选
     * （含自引用；仅翻译等价集成员）。未分组站点或页内无已链接文章 → 空 Map；批量查询，无逐行 N+1
     *
     * @param site     当前站点
     * @param articles 页内文章列表（须已 setSite）
     * @return 文章ID → exact 语言备选列表（未链接文章无条目）
     */
    public Map<Long, List<LangAlternate>> listExactAlternatesByArticles(Site site, List<Article> articles) {
        List<Site> members = siteService.listByLangGroup(site);
        if (members.size() < 2 || articles.isEmpty()) {
            return Collections.emptyMap();
        }
        // 站点按组内排序（order_, id_）——备选输出顺序与其它入口一致；无语言标签的站点跳过
        Map<Long, Site> siteById = new LinkedHashMap<>(members.size());
        for (Site member : members) {
            if (StringUtils.isNotBlank(member.getLang())) {
                siteById.put(member.getId(), member);
            }
        }
        Map<Long, List<Article>> membersByArticleId = articleI18nService
                .listMembersByArticleIds(articles.stream().map(Article::getId).toList());
        Map<Long, List<LangAlternate>> result = new HashMap<>(Math.max(16, membersByArticleId.size() * 2));
        for (Map.Entry<Long, List<Article>> entry : membersByArticleId.entrySet()) {
            // 组成员按站点分桶（组内每站点至多一员）
            Map<Long, Article> articleBySiteId = new HashMap<>(8);
            for (Article member : entry.getValue()) {
                articleBySiteId.put(member.getSiteId(), member);
            }
            List<LangAlternate> alternates = new ArrayList<>(siteById.size());
            for (Site memberSite : siteById.values()) {
                Article memberArticle = articleBySiteId.get(memberSite.getId());
                if (memberArticle == null) {
                    continue;
                }
                memberArticle.setSite(memberSite);
                alternates.add(new LangAlternate(memberSite, memberArticle.getUrl(), memberArticle.getFullUrl(),
                        true, memberSite.getId().equals(site.getId())));
            }
            result.put(entry.getKey(), alternates);
        }
        return result;
    }

    /**
     * 当前站点条目：URL 即当前页面自身（hreflang 自引用）
     */
    private LangAlternate currentAlternate(Site site, @Nullable Channel channel, @Nullable Article article) {
        if (article != null) {
            return new LangAlternate(site, article.getUrl(), article.getFullUrl(), true, true);
        }
        if (channel != null) {
            return new LangAlternate(site, channel.getUrl(), channel.getFullUrl(), true, true);
        }
        return new LangAlternate(site, site.getUrl(), site.getFullUrl(), true, true);
    }

    /**
     * 对等站点条目：按 Phase-3 映射规则表取目标 URL 与 exact 标记
     */
    private LangAlternate peerAlternate(Site peer, @Nullable Channel channel, @Nullable Article article,
                                        boolean isHome, boolean exactOnly) {
        if (article != null) {
            // 翻译等价集直链（multilingual P5）：显式链接的对等文章是精确译文
            Article peerArticle = articleI18nService.findByGroupAndSiteId(article.getId(), peer.getId());
            if (peerArticle != null) {
                peerArticle.setSite(peer);
                return new LangAlternate(peer, peerArticle.getUrl(), peerArticle.getFullUrl(), true, false);
            }
            // 未链接回退：栏目不是该文章的译文，exact 恒为 false
            return fallbackAlternate(peer, article.getChannel(), exactOnly);
        }
        if (channel != null) {
            Channel peerChannel = channelService.findBySiteIdAndSlug(peer.getId(), channel.getSlug());
            if (peerChannel != null) {
                return channelAlternate(peer, peerChannel, true);
            }
            return fallbackAlternate(peer, channel.getParent(), exactOnly);
        }
        return homeAlternate(peer, isHome);
    }

    /**
     * 回退条目（{@code exact=false}）：自 {@code channel} 起最近的对等栏目，整条链都没有则对等首页。
     * 调用方只要 exact 条目时不做查找——回退条目落在哪都会被过滤
     */
    private LangAlternate fallbackAlternate(Site peer, @Nullable Channel channel, boolean exactOnly) {
        Channel peerChannel = exactOnly ? null : nearestPeerChannel(peer, channel);
        return peerChannel != null ? channelAlternate(peer, peerChannel, false) : homeAlternate(peer, false);
    }

    /**
     * 自 {@code channel} 起沿父栏目上溯，取对等站点上第一个同别名栏目——两个语言站点的栏目树
     * 不必逐级一致，子栏目在对方没有对应时落到最近的共同上级（国内 → News），而不是一步退回首页。
     * 父栏目链未加载（sitemap 的瘦身结果集）时自然止于当前栏目
     *
     * @return 最近的对等栏目。整条链都无同别名栏目则返回 {@code null}
     */
    @Nullable
    private Channel nearestPeerChannel(Site peer, @Nullable Channel channel) {
        for (Channel current = channel; current != null; current = current.getParent()) {
            Channel peerChannel = channelService.findBySiteIdAndSlug(peer.getId(), current.getSlug());
            if (peerChannel != null) {
                return peerChannel;
            }
        }
        return null;
    }

    private LangAlternate channelAlternate(Site peer, Channel peerChannel, boolean exact) {
        // 重挂站点后再组装 URL，域名/子目录/静态页设置才会按对等站点计算（SiteMapController 同款手法）
        peerChannel.setSite(peer);
        return new LangAlternate(peer, peerChannel.getUrl(), peerChannel.getFullUrl(), exact, false);
    }

    private LangAlternate homeAlternate(Site peer, boolean exact) {
        return new LangAlternate(peer, peer.getUrl(), peer.getFullUrl(), exact, false);
    }

    /**
     * 一个语言备选项。带 getter 的不可变类而非 record——FreeMarker 的 BeansWrapper 按 JavaBean
     * 属性暴露成员，record 的组件访问器（{@code lang()}）不被识别为属性，模板 {@code ${alt.lang}}
     * 会取不到值
     */
    public static final class LangAlternate implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 目标语言站点（完整对象，模板可取 name/logo 等）
         */
        private final Site site;
        /**
         * 语言标签（site.langTag）
         */
        private final String lang;
        /**
         * 站内 URL（getUrl 口径：静态页开启时为静态 URL）
         */
        private final String url;
        /**
         * 绝对 URL（hreflang / sitemap 用）
         */
        private final String fullUrl;
        /**
         * true = 对等内容；false = 回退（对方首页或近似栏目）。hreflang 只允许输出 exact 条目
         */
        private final boolean exact;
        /**
         * 是否当前正在渲染的站点
         */
        private final boolean current;

        public LangAlternate(Site site, String url, String fullUrl, boolean exact, boolean current) {
            this.site = site;
            this.lang = site.getLangTag();
            this.url = url;
            this.fullUrl = fullUrl;
            this.exact = exact;
            this.current = current;
        }

        public Site getSite() {
            return site;
        }

        public String getLang() {
            return lang;
        }

        public String getUrl() {
            return url;
        }

        public String getFullUrl() {
            return fullUrl;
        }

        public boolean isExact() {
            return exact;
        }

        public boolean isCurrent() {
            return current;
        }
    }
}
