package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleExt;
import com.ujcms.cms.core.domain.ArticleI18n;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.mapper.ArticleI18nMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.exception.Http400Exception;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 文章翻译等价集 Service（multilingual P5，translation-links.md §3；
 * 过期锚点与译文草稿见 translation-workflow.md）。
 *
 * <p>公开读接口只返回可访问成员（isNormal 状态，SQL 过滤——hreflang 指向草稿/待审有害无益）；
 * 控制台读与不变量检查使用状态无关变体（译文草稿必须可见）。
 * 写接口维护三条不变量：两文章须属于同一语言组的不同站点；组内每站点至多一名成员；
 * v1 不做隐式并组（两侧均已入组时拒绝，先解除一侧）。
 * 关联即断言当前一致：链入成员写入过期锚点（解除后重联会重置过期状态，属预期语义）。
 *
 * @author PONY
 */
@Service
public class ArticleI18nService {
    private static final Logger logger = LoggerFactory.getLogger(ArticleI18nService.class);

    private final ArticleService articleService;
    private final ArticleI18nAnchorService anchorService;
    private final ArticleI18nMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public ArticleI18nService(ArticleService articleService, ArticleI18nAnchorService anchorService,
                              ArticleI18nMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.articleService = articleService;
        this.anchorService = anchorService;
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    // region 读

    /**
     * 该文章所在翻译组的全部成员文章（仅 isNormal 状态；按ID排序）。未入组返回 singletonList(article)
     *
     * @param article 文章
     * @return 成员文章列表
     */
    public List<Article> listByGroup(Article article) {
        ArticleI18n membership = mapper.select(article.getId());
        if (membership == null) {
            return Collections.singletonList(article);
        }
        return mapper.listArticleByGroupId(membership.getGroupId());
    }

    /**
     * 组内指定站点的成员文章（仅 isNormal 状态）
     *
     * @param articleId 文章ID
     * @param siteId    目标站点ID
     * @return 成员文章。未入组或该站点无成员则返回 {@code null}
     */
    @Nullable
    public Article findByGroupAndSiteId(Long articleId, Long siteId) {
        return mapper.findArticleByGroupAndSiteId(articleId, siteId);
    }

    /**
     * 批量：源文章ID → 其翻译组全部成员文章（含自身；仅 isNormal 状态）。
     * sitemap 分页专用（translation-links.md §5.2）：三次批量查询，无逐行 N+1。
     * 未入组的源文章不出现在结果中
     *
     * @param articleIds 源文章ID集合
     * @return 源文章ID → 成员文章列表
     */
    public Map<Long, List<Article>> listMembersByArticleIds(Collection<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ArticleI18n> memberships = mapper.listByArticleIds(articleIds);
        if (memberships.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> groupIds = memberships.stream().map(ArticleI18n::getGroupId).distinct().toList();
        // 全量成员行（含组外源文章看不到的对端成员）：articleId → groupId
        Map<Long, Long> groupByArticleId = new HashMap<>(32);
        for (ArticleI18n member : mapper.listByGroupIds(groupIds)) {
            groupByArticleId.put(member.getArticleId(), member.getGroupId());
        }
        // 成员文章按组分桶
        Map<Long, List<Article>> membersByGroupId = new HashMap<>(16);
        for (Article member : mapper.listArticleByGroupIds(groupIds)) {
            Long groupId = groupByArticleId.get(member.getId());
            if (groupId != null) {
                membersByGroupId.computeIfAbsent(groupId, k -> new ArrayList<>()).add(member);
            }
        }
        Map<Long, List<Article>> result = new HashMap<>(32);
        for (ArticleI18n membership : memberships) {
            List<Article> members = membersByGroupId.get(membership.getGroupId());
            if (members != null) {
                result.put(membership.getArticleId(), members);
            }
        }
        return result;
    }

    /**
     * 备选文章查询（翻译选择器；仅 isNormal 状态；按ID倒序）
     *
     * @param siteId    站点ID
     * @param channelId 栏目ID。可为 {@code null}
     * @param title     标题模糊匹配。可为 {@code null}
     * @return 文章列表（瘦身结果集）
     */
    public List<Article> listCandidates(Long siteId, @Nullable Long channelId, @Nullable String title) {
        return mapper.listCandidateArticles(siteId, channelId, title);
    }

    /**
     * 在目标栏目内查找可直接关联为译文的未入组文章（批量翻译“先关联已有译文、后新建草稿”，
     * article-translate.md §7）：别名精确相等优先，其次标题精确相等；同一依据命中多篇为歧义
     * （既不关联也不新建，交由人工处理）。状态无关但排除回收站——目标站点已有的草稿译文若不关联，
     * 批量翻译会为其再建一份重复草稿
     *
     * @param targetChannelId 目标栏目ID（源栏目的同别名栏目）
     * @param slug            源文章别名（仅自定义别名参与匹配；ID 哨兵值由调用方传 {@code null}）。可为 {@code null}
     * @param title           源文章标题
     * @return 匹配结果（never {@code null}；未命中为 {@link Match#NONE}）
     */
    public Match findUnlinkedMatch(Long targetChannelId, @Nullable String slug, String title) {
        String slugKey = StringUtils.trimToNull(slug);
        if (slugKey == null && StringUtils.isBlank(title)) {
            return Match.NONE;
        }
        List<Article> hits = mapper.listUnlinkedMatchArticles(targetChannelId, slugKey, title);
        if (slugKey != null) {
            List<Article> bySlug = hits.stream().filter(hit -> slugKey.equals(hit.getSlug())).toList();
            if (bySlug.size() == 1) {
                return new Match(bySlug.get(0), Match.Basis.SLUG);
            }
            if (bySlug.size() > 1) {
                return Match.AMBIGUOUS;
            }
        }
        List<Article> byTitle = hits.stream().filter(hit -> title.equals(hit.getTitle())).toList();
        if (byTitle.size() == 1) {
            return new Match(byTitle.get(0), Match.Basis.TITLE);
        }
        return byTitle.size() > 1 ? Match.AMBIGUOUS : Match.NONE;
    }

    /**
     * {@link #findUnlinkedMatch} 的结果
     *
     * @param article 命中的目标文章（瘦身结果集；建链前须经 select 完整加载）。歧义或未命中为 {@code null}
     * @param basis   匹配依据
     */
    public record Match(@Nullable Article article, Basis basis) {
        public static final Match NONE = new Match(null, Basis.NONE);
        public static final Match AMBIGUOUS = new Match(null, Basis.AMBIGUOUS);

        public boolean matched() {
            return article != null;
        }

        /**
         * 匹配依据：SLUG 别名相等；TITLE 标题相等；AMBIGUOUS 同一依据命中多篇；NONE 未命中
         */
        public enum Basis {
            SLUG, TITLE, AMBIGUOUS, NONE
        }
    }

    /**
     * 批量获取成员行（备选查询标记已入组文章用）
     *
     * @param articleIds 文章ID集合
     * @return 成员行列表
     */
    public List<ArticleI18n> listByArticleIds(Collection<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.listByArticleIds(articleIds);
    }

    /**
     * 该文章所在翻译组的全部成员行（含自身；过期判定用）。未入组返回空列表
     *
     * @param articleId 文章ID
     * @return 成员行列表
     */
    public List<ArticleI18n> listGroupRows(Long articleId) {
        ArticleI18n membership = mapper.select(articleId);
        if (membership == null) {
            return Collections.emptyList();
        }
        return mapper.listByGroupIds(List.of(membership.getGroupId()));
    }

    /**
     * 组内指定站点的成员文章（状态无关——控制台读专用，草稿译文必须可见；
     * 公开读路径用 {@link #findByGroupAndSiteId}）
     *
     * @param articleId 文章ID
     * @param siteId    目标站点ID
     * @return 成员文章。未入组或该站点无成员则返回 {@code null}
     */
    @Nullable
    public Article findMemberByGroupAndSiteId(Long articleId, Long siteId) {
        return mapper.findMemberArticleByGroupAndSiteId(articleId, siteId);
    }

    /**
     * 批量过期状态（文章列表徽标用）：文章ID → 是否“可能过期”。
     * 只包含已入组文章（未入组文章不出现在结果中）；按站点过滤，避免探测他站文章
     *
     * @param articleIds 文章ID集合
     * @param siteId     当前站点ID
     * @return 文章ID → 是否可能过期
     */
    public Map<Long, Boolean> listStaleByArticleIds(Collection<Long> articleIds, Long siteId) {
        if (articleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ArticleI18n> memberships = mapper.listBySiteArticleIds(articleIds, siteId);
        if (memberships.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> groupIds = memberships.stream().map(ArticleI18n::getGroupId).distinct().toList();
        Map<Long, List<ArticleI18n>> rowsByGroupId = new HashMap<>(16);
        for (ArticleI18n row : mapper.listByGroupIds(groupIds)) {
            rowsByGroupId.computeIfAbsent(row.getGroupId(), k -> new ArrayList<>()).add(row);
        }
        Map<Long, Boolean> result = new HashMap<>(32);
        for (ArticleI18n membership : memberships) {
            List<ArticleI18n> groupRows = rowsByGroupId.getOrDefault(membership.getGroupId(),
                    Collections.emptyList());
            result.put(membership.getArticleId(), ArticleI18n.isStale(membership, groupRows));
        }
        return result;
    }

    // endregion

    // region 写

    /**
     * 将两篇文章链入同一翻译组。规则（translation-links.md §3.2）：
     * 同语言组、不同站点、组内每站点至多一员（指名冲突，状态无关——草稿成员同样占位）、
     * 双方均已入组时拒绝（v1 不隐式并组）。自链为空操作。
     * 链入成员写入过期锚点：content_date_ = 自身 modified_（加入既有组时经
     * {@link ArticleI18nAnchorService#clampJoinContentDate} 钳制），synced_date_ = now
     *
     * @param article 当前文章（须带 site 关联）
     * @param target  目标文章（须带 site 关联）
     */
    @Transactional(rollbackFor = Exception.class)
    public void link(Article article, Article target) {
        if (Objects.equals(article.getId(), target.getId())) {
            return;
        }
        validatePair(article, target);
        ArticleI18n current = mapper.select(article.getId());
        ArticleI18n other = mapper.select(target.getId());
        if (current != null && other != null) {
            if (Objects.equals(current.getGroupId(), other.getGroupId())) {
                return;
            }
            throw new Http400Exception(String.format(
                    "Both articles already belong to translation groups (article %s in group %s, article %s in group %s)."
                            + " Unlink one first — implicit group merging is not supported.",
                    article.getId(), current.getGroupId(), target.getId(), other.getGroupId()));
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (current == null && other == null) {
            long groupId = snowflakeSequence.nextId();
            mapper.insert(new ArticleI18n(article.getId(), groupId, article.getModified(), now));
            mapper.insert(new ArticleI18n(target.getId(), groupId, target.getModified(), now));
            return;
        }
        // 一侧已入组：另一侧加入该组，加入前校验该组在其站点上尚无成员（状态无关）
        ArticleI18n existing = current != null ? current : other;
        Article joining = current != null ? target : article;
        Long existingArticleId = current != null ? article.getId() : target.getId();
        Article occupant = mapper.findMemberArticleByGroupAndSiteId(existingArticleId, joining.getSiteId());
        if (occupant != null && !Objects.equals(occupant.getId(), joining.getId())) {
            throw new Http400Exception(String.format(
                    "The translation group already contains article %s (%s) for site %s. Unlink it first.",
                    occupant.getId(), occupant.getTitle(), joining.getSiteId()));
        }
        OffsetDateTime contentDate = anchorService.clampJoinContentDate(existing.getGroupId(), joining.getModified());
        mapper.insert(new ArticleI18n(joining.getId(), existing.getGroupId(), contentDate, now));
    }

    /**
     * 按种子来源键配对建链（install-flow D50）：语言组内两个站点上来源键（{@code origin_id_}）相同的
     * 主题种子文章，是同一篇内容的两个语言版本——依据是出处，不是别名/标题猜测。两站播种同一主题时
     * 全部种子文章成对；播种不同主题时，只有作者刻意复用来源键的文章成对。
     *
     * <p>站点内同一来源键出现多篇（先后播种过两个主题）为歧义，不建链。已同组为幂等命中；
     * {@link #link} 拒绝的配对（该组在对方站点已有其他成员、双方各有其组）计为冲突并跳过，
     * 不中断其余配对——拒绝发生在任何写入之前，且未穿过事务代理，不会污染本事务
     *
     * @param site 当前站点
     * @param peer 同语言组的另一站点
     * @return 建链统计
     */
    @Transactional(rollbackFor = Exception.class)
    public SeedLinkResult linkSeedPeers(Site site, Site peer) {
        Map<String, List<Article>> own = seedArticlesByOriginId(site);
        Map<String, List<Article>> theirs = seedArticlesByOriginId(peer);
        List<SeedPair> pairs = new ArrayList<>();
        int ambiguous = 0;
        for (Map.Entry<String, List<Article>> entry : own.entrySet()) {
            List<Article> peers = theirs.get(entry.getKey());
            if (peers == null) {
                continue;
            }
            if (entry.getValue().size() > 1 || peers.size() > 1) {
                ambiguous++;
                continue;
            }
            pairs.add(new SeedPair(entry.getValue().get(0), peers.get(0)));
        }
        if (pairs.isEmpty()) {
            return new SeedLinkResult(0, 0, 0, ambiguous);
        }
        List<Long> ids = new ArrayList<>(pairs.size() * 2);
        for (SeedPair pair : pairs) {
            ids.add(pair.article().getId());
            ids.add(pair.peer().getId());
        }
        Map<Long, Long> groupByArticleId = new HashMap<>(Math.max(16, ids.size() * 2));
        for (ArticleI18n row : mapper.listByArticleIds(ids)) {
            groupByArticleId.put(row.getArticleId(), row.getGroupId());
        }
        int linked = 0;
        int already = 0;
        int conflicts = 0;
        for (SeedPair pair : pairs) {
            Long groupId = groupByArticleId.get(pair.article().getId());
            if (groupId != null && groupId.equals(groupByArticleId.get(pair.peer().getId()))) {
                already++;
                continue;
            }
            try {
                link(pair.article(), pair.peer());
                linked++;
            } catch (Http400Exception e) {
                conflicts++;
                logger.warn("Seed translation link skipped: originId={} article={} peer={} — {}",
                        pair.article().getOriginId(), pair.article().getId(), pair.peer().getId(), e.getMessage());
            }
        }
        return new SeedLinkResult(linked, already, conflicts, ambiguous);
    }

    /**
     * 站点种子文章按来源键分桶（保持ID顺序）；文章挂上站点关联（{@link #link} 的语言组校验需要）
     */
    private Map<String, List<Article>> seedArticlesByOriginId(Site site) {
        Map<String, List<Article>> result = new LinkedHashMap<>(64);
        for (Article article : mapper.listSeedArticles(site.getId())) {
            article.setSite(site);
            result.computeIfAbsent(article.getOriginId(), k -> new ArrayList<>(1)).add(article);
        }
        return result;
    }

    private record SeedPair(Article article, Article peer) {
    }

    /**
     * {@link #linkSeedPeers} 的统计
     *
     * @param linked    本次新建链的配对数
     * @param already   已在同一翻译组的配对数（幂等续装命中）
     * @param conflicts 被不变量拒绝而跳过的配对数
     * @param ambiguous 站点内来源键不唯一而跳过的来源键数
     */
    public record SeedLinkResult(int linked, int already, int conflicts, int ambiguous) {
    }

    /**
     * 将文章移出其翻译组（组内仅剩一员时组自然消亡，无需清理）
     *
     * @param articleId 文章ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void unlink(Long articleId) {
        mapper.deleteByArticleId(articleId);
    }

    /**
     * 以 article 为基准的集合式写入（ArticleForm 保存用）：per-site 目标文章，{@code null} 值=解除该站点链接。
     * 未出现在 map 中的站点保持不变
     *
     * @param article          当前文章（须带 site 关联）
     * @param siteIdToTargetId 站点ID → 目标文章ID（或 {@code null}）
     */
    @Transactional(rollbackFor = Exception.class)
    public void relink(Article article, Map<Long, @Nullable Long> siteIdToTargetId) {
        for (Map.Entry<Long, @Nullable Long> entry : siteIdToTargetId.entrySet()) {
            Long siteId = entry.getKey();
            Long targetId = entry.getValue();
            if (Objects.equals(siteId, article.getSiteId())) {
                throw new Http400Exception(String.format(
                        "Cannot relink against the article's own site. site: %s", siteId));
            }
            // 状态无关占位查询：草稿成员同样占位，否则集合式写入会突破“一站一员”
            Article occupant = mapper.findMemberArticleByGroupAndSiteId(article.getId(), siteId);
            if (targetId == null) {
                if (occupant != null) {
                    unlink(occupant.getId());
                }
                continue;
            }
            if (occupant != null && Objects.equals(occupant.getId(), targetId)) {
                continue;
            }
            Article target = Optional.ofNullable(articleService.select(targetId)).orElseThrow(() ->
                    new Http400Exception(Article.NOT_FOUND + targetId));
            if (!Objects.equals(target.getSiteId(), siteId)) {
                throw new Http400Exception(String.format(
                        "Target article %s does not belong to site %s (its site: %s)",
                        targetId, siteId, target.getSiteId()));
            }
            // 集合式写入语义：该站点槽位被其它文章占用时先解除再链入
            if (occupant != null) {
                unlink(occupant.getId());
            }
            link(article, target);
        }
    }

    /**
     * 新增译文（translation-workflow.md）：在同语言组的目标站点栏目下创建当前文章的草稿副本，
     * 并链入当前文章的翻译组。副本为草稿状态（构造上不触发审核流），不携带标签（译者补充
     * 目标语言标签），srcId 留空（翻译组成员关系即出处，非空 srcId 会触发控制台推送相关闸门）。
     * 定制字段以源栏目模型口径读出、插入时按目标栏目模型重组，未知字段静默丢弃
     *
     * @param source        源文章（须经 select 完整加载，带 site/channel/ext 关联）
     * @param targetSite    目标站点（须为同语言组的其它正常站点，由调用方解析校验）
     * @param targetChannel 目标栏目（须属于目标站点）
     * @param user          操作用户
     * @return 创建的草稿文章
     */
    @Transactional(rollbackFor = Exception.class)
    public Article createTranslation(Article source, Site targetSite, Channel targetChannel, User user) {
        if (!Objects.equals(targetChannel.getSiteId(), targetSite.getId())) {
            throw new Http400Exception(String.format(
                    "Target channel %s does not belong to site %s (its site: %s)",
                    targetChannel.getId(), targetSite.getId(), targetChannel.getSiteId()));
        }
        // 状态无关占位检查：该组在目标站点已有成员（含草稿）则拒绝
        Article occupant = mapper.findMemberArticleByGroupAndSiteId(source.getId(), targetSite.getId());
        if (occupant != null) {
            throw new Http400Exception(String.format(
                    "The translation group already contains article %s (%s) for site %s.",
                    occupant.getId(), occupant.getTitle(), targetSite.getId()));
        }
        Article bean = new Article();
        bean.setSiteId(targetSite.getId());
        bean.setChannelId(targetChannel.getId());
        bean.setOrgId(user.getOrgId());
        bean.setStatus(Article.STATUS_DRAFT);
        // ext 按值克隆（决不共享实例）。copyForChild 清掉只属于原文章自己的状态：静态页输出、审核流程、全部统计计数；
        // 译文草稿另外不带 AI 标记，mains/clobs 由插入时按目标模型重组
        ArticleExt ext = source.getExt().copyForChild();
        ext.setAiGenerated(false);
        ext.setMainsJson(null);
        ext.setClobsJson(null);
        bean.setExt(ext);
        bean.setCustoms(new HashMap<>(source.getCustoms()));
        articleService.insert(bean, user.getId(), Collections.emptyList());
        bean.setSite(targetSite);
        link(source, bean);
        return bean;
    }

    /**
     * 两篇文章必须属于同一语言组的不同站点
     */
    private void validatePair(Article article, Article target) {
        if (Objects.equals(article.getSiteId(), target.getSiteId())) {
            throw new Http400Exception(String.format(
                    "Translation links require different sites. Both articles belong to site %s",
                    article.getSiteId()));
        }
        Site site = article.getSite();
        Site targetSite = target.getSite();
        if (!Objects.equals(site.getLangGroupMasterId(), targetSite.getLangGroupMasterId())) {
            throw new Http400Exception(String.format(
                    "Translation links require sites of the same language group."
                            + " site %s (group master %s) vs site %s (group master %s)",
                    site.getId(), site.getLangGroupMasterId(), targetSite.getId(), targetSite.getLangGroupMasterId()));
        }
    }

    // endregion
}
