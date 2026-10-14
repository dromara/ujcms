package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Tag;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.lucene.ArticleLuceneArgs;
import com.ujcms.cms.core.lucene.domain.EsArticle;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.common.query.OffsetLimitRequest;
import com.ujcms.common.query.QueryParser;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 相关文章读取与候选生成（docs/spec/article-related/plan.md §3）。
 * <p>
 * 手选列表由 {@link ArticleService#listRelated} 提供；本服务负责两件事：
 * 前台渲染时把手选列表按 {@code limit} 自动补齐（{@link #listForRender}），
 * 以及为控制台"智能推荐"生成启发式候选池（{@link #candidates}）——标题相似（全文索引）、共同标签、同栏目
 * 三路信号合并打分，不依赖向量与大模型，社区版可用；商业版 T2 的 AI 重排在此候选池之上进行。
 *
 * @author PONY
 */
@Service
public class ArticleRelatedService {
    /**
     * 候选池默认大小（AI 重排的输入规模）
     */
    public static final int CANDIDATE_LIMIT = 30;
    /**
     * 候选池上限
     */
    public static final int CANDIDATE_MAX = 60;
    private static final int LUCENE_LIMIT = 20;
    private static final int TAG_SEED_LIMIT = 5;
    private static final int PER_TAG_LIMIT = 10;
    private static final int CHANNEL_LIMIT = 10;
    /**
     * 打分权重：标题相似按名次线性衰减，共享标签按个数累加，同栏目固定加分，新鲜度按一年指数衰减
     */
    private static final double WEIGHT_TITLE = 3.0;
    private static final double WEIGHT_TAG = 2.0;
    private static final double WEIGHT_CHANNEL = 1.0;
    private static final double WEIGHT_FRESHNESS = 0.5;
    private static final double FRESHNESS_DAYS = 365.0;

    public static final String HINT_TITLE_SIMILAR = "titleSimilar";
    public static final String HINT_SHARED_TAGS = "sharedTags";
    public static final String HINT_SAME_CHANNEL = "sameChannel";

    private final ArticleService articleService;
    private final ArticleLucene articleLucene;

    public ArticleRelatedService(ArticleService articleService, ArticleLucene articleLucene) {
        this.articleService = articleService;
        this.articleLucene = articleLucene;
    }

    /**
     * 候选生成的种子。已保存的文章用 {@link #seedOf(Article)}；未保存的新文章由控制台按表单当前值组装
     *
     * @param siteId      站点ID
     * @param channelId   栏目ID，可为空
     * @param title       标题
     * @param seoKeywords SEO 关键词，可为空
     * @param tags        标签列表
     * @param excludeId   自身ID（未保存时为空）
     */
    public record Seed(Long siteId, @Nullable Long channelId, String title, @Nullable String seoKeywords,
                       List<Tag> tags, @Nullable Long excludeId) {
    }

    public static Seed seedOf(Article article) {
        return new Seed(article.getSiteId(), article.getChannelId(), article.getTitle(), article.getSeoKeywords(),
                article.getTags(), article.getId());
    }

    /**
     * 候选项：文章、得分与解释（{@code hints} 为信号代码，{@code sharedTags} 为共同标签名）
     */
    public record Candidate(Article article, double score, List<String> hints, List<String> sharedTags) {
    }

    /**
     * 控制台候选视图（启发式与 AI 重排共用；{@code reason} 仅 AI 重排给出）。扁平结构，不带文章全图
     */
    public record CandidateView(Long id, String title, String url, @Nullable String image,
                                OffsetDateTime publishDate, Short status, Long channelId, String channelName,
                                double score, List<String> hints, List<String> sharedTags,
                                @Nullable String reason) {
        public static CandidateView of(Candidate candidate, @Nullable String reason) {
            Article a = candidate.article();
            return new CandidateView(a.getId(), a.getTitle(), a.getUrl(), a.getImage(), a.getPublishDate(),
                    a.getStatus(), a.getChannelId(), a.getChannel().getName(), candidate.score(),
                    candidate.hints(), candidate.sharedTags(), reason);
        }

        public CandidateView withReason(@Nullable String newReason, double newScore) {
            return new CandidateView(id, title, url, image, publishDate, status, channelId, channelName,
                    newScore, hints, sharedTags, newReason);
        }
    }

    /**
     * 渲染结果：手选在前、自动补齐在后；{@code manualCount} 为手选条数
     */
    public record RelatedResult(List<Article> list, int manualCount) {
    }

    /**
     * 前台相关文章列表：手选（已发布/已归档，按 order_）优先，不足 {@code limit} 且 {@code fill} 时用候选池补齐。
     * 无手选数据的站点得到的就是过去 {@code [@EsArticleList q=article.title]} 一类的自动结果
     *
     * @param article   当前文章
     * @param limit     条数
     * @param fill      是否自动补齐
     * @param channelId 补齐时限定的栏目（含子栏目），可为空
     */
    public RelatedResult listForRender(Article article, int limit, boolean fill, @Nullable Long channelId) {
        List<Article> manual = articleService.listRelated(article.getId());
        List<Article> list = new ArrayList<>(manual.size() > limit ? manual.subList(0, limit) : manual);
        int manualCount = list.size();
        if (fill && list.size() < limit) {
            Set<Long> exclude = list.stream().map(Article::getId).collect(Collectors.toCollection(HashSet::new));
            exclude.add(article.getId());
            Seed seed = seedOf(article);
            if (channelId != null) {
                seed = new Seed(seed.siteId(), channelId, seed.title(), seed.seoKeywords(), seed.tags(), seed.excludeId());
            }
            for (Candidate candidate : candidates(seed, exclude, limit - list.size(), channelId != null)) {
                list.add(candidate.article());
            }
        }
        return new RelatedResult(list, manualCount);
    }

    /**
     * 启发式候选池。三路信号（标题相似 / 共同标签 / 同栏目）各自取前 N 后合并，按加权得分排序取前 {@code limit}；
     * 只含已发布文章，排除 {@code exclude} 与种子自身
     *
     * @param seed            种子
     * @param exclude         排除的文章ID
     * @param limit           条数
     * @param restrictChannel 是否把三路信号都限定在种子栏目（含子栏目）内
     */
    public List<Candidate> candidates(Seed seed, Collection<Long> exclude, int limit, boolean restrictChannel) {
        Set<Long> excludeIds = new HashSet<>(exclude);
        if (seed.excludeId() != null) {
            excludeIds.add(seed.excludeId());
        }
        Map<Long, Double> scores = new LinkedHashMap<>();
        Map<Long, Set<String>> hints = new HashMap<>();
        Map<Long, List<String>> sharedTags = new HashMap<>();
        Map<Long, Article> hydrated = new HashMap<>();

        // 1. 标题相似：全文索引按标题（+SEO关键词）检索，名次线性衰减计分
        String q = StringUtils.isNotBlank(seed.seoKeywords())
                ? seed.title() + " " + seed.seoKeywords() : seed.title();
        if (StringUtils.isNotBlank(q)) {
            ArticleLuceneArgs args = new ArticleLuceneArgs();
            args.setTitle(q);
            args.setBody(q);
            args.setSiteId(seed.siteId());
            args.setEnabled(true);
            args.setStatus(Collections.singletonList((int) Article.STATUS_PUBLISHED));
            args.setExcludeIds(new ArrayList<>(excludeIds));
            if (restrictChannel && seed.channelId() != null) {
                args.setChannelIds(Collections.singletonList(seed.channelId()));
                args.setIncludeSubChannel(true);
            }
            List<EsArticle> hits = articleLucene.findAll(args, null, new OffsetLimitRequest(0, LUCENE_LIMIT))
                    .getContent();
            int size = hits.size();
            for (int i = 0; i < size; i++) {
                Long id = hits.get(i).getId();
                scores.merge(id, WEIGHT_TITLE * (size - i) / size, Double::sum);
                hints.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(HINT_TITLE_SIMILAR);
            }
        }

        // 2. 共同标签：每个标签取最新 N 篇，命中一次加一份权重
        List<Tag> tags = seed.tags() != null ? seed.tags() : Collections.emptyList();
        for (Tag tag : tags.stream().limit(TAG_SEED_LIMIT).toList()) {
            ArticleArgs args = baseArgs(seed, excludeIds, restrictChannel).tagId(tag.getId());
            for (Article article : articleService.selectList(args, 0, PER_TAG_LIMIT)) {
                scores.merge(article.getId(), WEIGHT_TAG, Double::sum);
                hints.computeIfAbsent(article.getId(), k -> new LinkedHashSet<>()).add(HINT_SHARED_TAGS);
                sharedTags.computeIfAbsent(article.getId(), k -> new ArrayList<>()).add(tag.getName());
                hydrated.putIfAbsent(article.getId(), article);
            }
        }

        // 3. 同栏目最新
        if (seed.channelId() != null) {
            ArticleArgs args = baseArgs(seed, excludeIds, false).channelId(seed.channelId());
            for (Article article : articleService.selectList(args, 0, CHANNEL_LIMIT)) {
                scores.merge(article.getId(), WEIGHT_CHANNEL, Double::sum);
                hints.computeIfAbsent(article.getId(), k -> new LinkedHashSet<>()).add(HINT_SAME_CHANNEL);
                hydrated.putIfAbsent(article.getId(), article);
            }
        }
        if (scores.isEmpty()) {
            return Collections.emptyList();
        }

        // 4. 补齐全文检索命中的文章对象；同栏目的行不再重复加分（信号 3 已计）
        List<Long> missing = scores.keySet().stream().filter(id -> !hydrated.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            articleService.listByIds(missing).forEach(article -> hydrated.put(article.getId(), article));
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<Candidate> result = new ArrayList<>(scores.size());
        for (Map.Entry<Long, Double> entry : scores.entrySet()) {
            Article article = hydrated.get(entry.getKey());
            if (article == null || article.getStatus() != Article.STATUS_PUBLISHED
                    || excludeIds.contains(article.getId())) {
                continue;
            }
            double score = entry.getValue();
            if (seed.channelId() != null && Objects.equals(article.getChannelId(), seed.channelId())
                    && !hints.get(article.getId()).contains(HINT_SAME_CHANNEL)) {
                score += WEIGHT_CHANNEL;
                hints.get(article.getId()).add(HINT_SAME_CHANNEL);
            }
            long ageDays = Math.max(0, Duration.between(article.getPublishDate(), now).toDays());
            score += WEIGHT_FRESHNESS * Math.exp(-ageDays / FRESHNESS_DAYS);
            result.add(new Candidate(article, score, new ArrayList<>(hints.get(article.getId())),
                    sharedTags.getOrDefault(article.getId(), Collections.emptyList())));
        }
        result.sort(Comparator.comparingDouble(Candidate::score).reversed()
                .thenComparing(c -> c.article().getPublishDate(), Comparator.reverseOrder()));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    private static ArticleArgs baseArgs(Seed seed, Set<Long> excludeIds, boolean restrictChannel) {
        ArticleArgs args = ArticleArgs.of().siteId(seed.siteId())
                .status(Collections.singletonList(Article.STATUS_PUBLISHED));
        if (!excludeIds.isEmpty()) {
            args.excludeIds(excludeIds);
        }
        if (restrictChannel && seed.channelId() != null) {
            args.channelAncestorId(seed.channelId());
        }
        args.getQueryMap().put(QueryParser.ORDER_BY, "publishDate_desc,id_desc");
        return args;
    }
}
