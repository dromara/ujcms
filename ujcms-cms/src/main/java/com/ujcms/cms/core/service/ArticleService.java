package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.component.ContentStatEvictor;
import com.ujcms.cms.core.component.SlugGenerator;
import com.ujcms.cms.core.domain.*;
import com.ujcms.cms.core.domain.generated.GeneratedArticle;
import com.ujcms.cms.core.generator.HtmlService;
import com.ujcms.cms.core.listener.ArticleDeleteListener;
import com.ujcms.cms.core.listener.ArticlePublishListener;
import com.ujcms.cms.core.listener.ArticleRejectListener;
import com.ujcms.cms.core.listener.ChannelDeleteListener;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.lucene.ArticleLuceneArgs;
import com.ujcms.cms.core.lucene.domain.EsArticle;
import com.ujcms.cms.core.mapper.ArticleExtMapper;
import com.ujcms.cms.core.mapper.ArticleI18nMapper;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.SiteMapper;
import com.ujcms.cms.core.mapper.ArticleRelatedMapper;
import com.ujcms.cms.core.mapper.ArticleTagMapper;
import com.ujcms.cms.core.mapper.ArticleTopicMapper;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.db.order.OrderEntityUtils;
import com.ujcms.common.query.OffsetLimitRequest;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.web.exception.LogicException;
import info.debatty.java.stringsimilarity.JaroWinkler;
import org.apache.commons.lang3.StringUtils;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.cms.core.security.PolicyTier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.*;

import static com.ujcms.cms.core.domain.Article.*;

/**
 * 文章 Service
 *
 * @author PONY
 */
@Service
public class ArticleService implements ChannelDeleteListener, UserDeleteListener, SiteDeleteListener {
    private static final Logger logger = LoggerFactory.getLogger(ArticleService.class);

    private final HtmlService htmlService;
    private final HtmlSanitizeService sanitizeService;
    private final ObjectProvider<ArticleProcessPort> processPort;
    private final ChannelService channelService;
    private final TagService tagService;
    private final BlockItemService blockItemService;
    private final ArticleLucene articleLucene;
    private final ArticleMapper mapper;
    private final ArticleExtMapper extMapper;
    private final ArticleTagMapper articleTagMapper;
    private final ArticleTopicMapper articleTopicMapper;
    private final ArticleRelatedMapper articleRelatedMapper;
    private final ArticleI18nMapper articleI18nMapper;
    private final ArticleI18nAnchorService articleI18nAnchorService;
    private final TopicService topicService;
    private final AttachmentService attachmentService;
    private final SnowflakeSequence snowflakeSequence;
    private final SlugGenerator slugGenerator;
    private final SiteMapper siteMapper;
    private final ContentStatEvictor contentStatEvictor;

    public ArticleService(HtmlService htmlService, HtmlSanitizeService sanitizeService,
                          ObjectProvider<ArticleProcessPort> processPort,
            ChannelService channelService, TagService tagService, BlockItemService blockItemService,
            ArticleLucene articleLucene, ArticleMapper mapper, ArticleExtMapper extMapper,
                          ArticleTagMapper articleTagMapper, ArticleTopicMapper articleTopicMapper,
                          ArticleRelatedMapper articleRelatedMapper,
                          ArticleI18nMapper articleI18nMapper, ArticleI18nAnchorService articleI18nAnchorService,
                          TopicService topicService,
                          AttachmentService attachmentService, SnowflakeSequence snowflakeSequence,
                          SlugGenerator slugGenerator, ContentStatEvictor contentStatEvictor,
                          SiteMapper siteMapper) {
        this.htmlService = htmlService;
        this.sanitizeService = sanitizeService;
        this.processPort = processPort;
        this.channelService = channelService;
        this.tagService = tagService;
        this.blockItemService = blockItemService;
        this.articleLucene = articleLucene;
        this.mapper = mapper;
        this.extMapper = extMapper;
        this.articleTagMapper = articleTagMapper;
        this.articleTopicMapper = articleTopicMapper;
        this.articleRelatedMapper = articleRelatedMapper;
        this.articleI18nMapper = articleI18nMapper;
        this.articleI18nAnchorService = articleI18nAnchorService;
        this.topicService = topicService;
        this.attachmentService = attachmentService;
        this.snowflakeSequence = snowflakeSequence;
        this.slugGenerator = slugGenerator;
        this.siteMapper = siteMapper;
        this.contentStatEvictor = contentStatEvictor;
    }

    /**
     * 别名留空时由服务端生成（docs/spec/permalinks/plan.md D4）：标题 slugify → 拼音（站点开关）→ ID 字符串（哨兵值），
     * 站内唯一（冲突追加 -2、-3…）。标题变更不重生成；清空别名即触发重生成。
     * 此路径为采集、微信同步、批量翻译、种子导入共用，故只做离线确定性生成，不做 AI 调用
     */
    private void fillSlug(Article bean) {
        if (StringUtils.isBlank(bean.getSlug())) {
            // 站点行锁之下、以锁定读查重（D22）：同名文章同时新建也不会生成同一个别名
            bean.setSlug(slugGenerator.generate(bean.getSiteId(), bean.getTitle(), bean.getId(),
                    SlugGenerator.ARTICLE_MAX_LENGTH,
                    slug -> !mapper.listIdsBySlugForUpdate(slug, bean.getSiteId(), bean.getId()).isEmpty()));
        }
    }

    /**
     * 根据 站点ID 与 别名 查询文章（固定链接解析 D7）
     */
    @Nullable
    public Article findBySiteIdAndSlug(Long siteId, String slug) {
        List<Article> list = PageMethod.offsetPage(0, 1, false).doSelectPage(() ->
                mapper.findBySiteIdAndSlug(siteId, slug));
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    /**
     * 别名是否已被站内其他文章占用
     *
     * @param excludeId 排除的文章ID（更新时排除自身），可为 {@code null}
     */
    public boolean existsBySlug(String slug, Long siteId, @Nullable Long excludeId) {
        return mapper.existsBySlug(slug, siteId, excludeId) > 0;
    }

    private void insertRelatedList(Long articleId, Long siteId, Long userId, List<String> tagNames) {
        int tagOrder = 0;
        for (String name : tagNames) {
            Tag tag = tagService.selectByName(siteId, name);
            if (tag == null) {
                tag = new Tag(siteId, userId, name);
                tag.setRefers(1);
                tagService.insert(tag);
            } else {
                tag.setRefers(tag.getRefers() + 1);
                tagService.update(tag);
            }
            articleTagMapper.insert(new ArticleTag(articleId, tag.getId(), tagOrder++));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Article bean, Long userId, List<String> tagNames) {
        insert(bean, userId, tagNames, false);
    }

    /**
     * 插入从别处迁来的文章：创建时间、修改时间沿用文章对象上已经设好的值（源系统里的时间），不改写成“现在”。
     * 其余与 {@link #insert(Article, Long, List)} 相同。数据迁移用
     * （docs/spec/article-management/plan.md Phase 5b）
     */
    @Transactional(rollbackFor = Exception.class)
    public void insertMigrated(Article bean, Long userId, List<String> tagNames) {
        // 迁入的是历史内容：不进审批流程，也不触发发布监听（搜索引擎推送、关注通知）；全文索引照常
        doInsert(bean, userId, tagNames, true, true, false);
    }

    /**
     * 插入文章，可跳过审批流程（theme-marketplace §5.4 skipProcess 重载）：内容种子导入目标为
     * 带 {@code processKey} 的 linked 操作员栏目时，演示数据直接以已发布状态落库，
     * 恒不生成 Flowable 审批任务。
     *
     * @param skipProcess 为 true 时即使栏目配置了 processKey 也不启动流程
     */
    @Transactional(rollbackFor = Exception.class)
    public void insert(Article bean, Long userId, List<String> tagNames, boolean skipProcess) {
        doInsert(bean, userId, tagNames, skipProcess, false, true);
    }

    /**
     * @param skipProcess   为 true 时即使栏目配置了 processKey 也不启动流程
     * @param keepDates     为 true 时沿用 bean 上已有的创建、修改时间（数据迁移）
     * @param fireListeners 为 false 时不触发发布监听（数据迁移：历史文章不推送搜索引擎、不发关注通知）
     */
    private void doInsert(Article bean, Long userId, List<String> tagNames, boolean skipProcess, boolean keepDates,
                          boolean fireListeners) {
        bean.setUserId(userId);
        bean.setModifiedUserId(userId);
        if (!keepDates) {
            OffsetDateTime now = OffsetDateTime.now();
            bean.setCreated(now);
            bean.setModified(now);
        }
        bean.setId(snowflakeSequence.nextId());
        bean.setOrder(bean.getId());
        fillSlug(bean);
        bean.setWithImage(StringUtils.isNotBlank(bean.getImage()));
        Channel channel = Optional.ofNullable(channelService.select(bean.getChannelId())).orElseThrow(() ->
                new IllegalArgumentException(Channel.NOT_FOUND + bean.getChannelId()));
        String processKey = channel.getProcessKey();
        // 待发布（上线时间在将来）与已发布一样要过审批：否则把上线时间往后填一分钟，文章就绕开流程、到点自动上线了
        // （docs/spec/article-management/plan.md D4）
        boolean toPublish = bean.getStatus() == STATUS_PUBLISHED || bean.getStatus() == STATUS_READY;
        if (!skipProcess && toPublish && StringUtils.isNotBlank(processKey)) {
            startProcess(bean, processKey, userId);
        }
        ArticleExt ext = bean.getExt();
        ext.setId(bean.getId());
        ext.setText(sanitizeService.sanitize(ext.getText(), PolicyTier.RICH));
        Model model = channel.getArticleModel();
        bean.disassembleCustoms(model, sanitizeService);
        mapper.insert(bean);
        extMapper.insert(ext);
        insertRelatedList(bean.getId(), bean.getSiteId(), userId, tagNames);
        attachmentService.insertRefer(GeneratedArticle.TABLE_NAME, bean.getId(), bean.getAttachmentUrls(model));
        indexAfterCommit(bean.getId());
        // 读取最终状态：有流程时 startProcess 可能已将状态改为待审核
        if (fireListeners) {
            firePublishListeners(bean);
        }
        contentStatEvictor.article(bean.getSiteId());
    }

    /**
     * 新增文章并同步扩展栏目（docs/spec/article-data-permission/plan.md D20）。同一事务：扩展栏目的拒绝不再留下
     * 一篇没有扩展栏目的新文章。站点行锁在先（D22 的锁序）
     */
    @Transactional(rollbackFor = Exception.class)
    public void insert(Article bean, Long userId, List<String> tagNames,
                       @Nullable List<Long> desiredRefers, @Nullable List<Long> desiredCopies, User operator) {
        insert(bean, userId, tagNames, desiredRefers, desiredCopies, null, null, operator);
    }

    /**
     * 新增文章，连同扩展栏目、专题、相关文章一起，在一个事务里（docs/spec/article-management/plan.md Phase 1.8）：
     * 专题或相关文章被拒绝（专题已删除、跨站点、超出上限……）时，不再留下一篇已保存却缺了这两样的文章。
     *
     * @param topicIds 所属专题；{@code null} 表示不设置
     * @param related  相关文章；{@code null} 表示不设置
     * @return 相关文章里“互相关联”发生变化的对方文章：它们的列表变了，调用方据此重新生成其静态页
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Article> insert(Article bean, Long userId, List<String> tagNames,
                                @Nullable List<Long> desiredRefers, @Nullable List<Long> desiredCopies,
                                @Nullable List<Long> topicIds, @Nullable List<RelatedItem> related, User operator) {
        siteMapper.lockById(bean.getSiteId());
        insert(bean, userId, tagNames);
        assignExtendedChannels(bean.getId(), desiredRefers, desiredCopies, operator);
        return assignTopicsAndRelated(bean.getId(), topicIds, related);
    }

    private List<Article> assignTopicsAndRelated(Long articleId, @Nullable List<Long> topicIds,
                                                 @Nullable List<RelatedItem> related) {
        if (topicIds != null) {
            assignToTopics(articleId, topicIds);
        }
        return related != null ? assignRelated(articleId, related) : Collections.emptyList();
    }

    /**
     * 对站点行加行锁（docs/spec/article-data-permission/plan.md D22）：生成别名在它之下进行，所以凡是会锁文章行的落位
     * 写入（推送、扩展栏目同步、改换栏目、栏目合并）都先锁站点、再锁文章——同一个锁序，不会互相等成死锁。
     * 须在调用方已开启的事务里执行（{@link Propagation#MANDATORY}）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockSite(Long siteId) {
        siteMapper.lockById(siteId);
    }

    /**
     * 启动审批流程。社区版无 {@link ArticleProcessPort} 实现：栏目的 processKey 视同无流程，文章直接发布——
     * “发布”同样要看上下线时间（{@link Article#adjustStatus(Short)}）：上线时间在将来的仍是待发布，不会提前上线
     */
    private void startProcess(Article article, String processKey, Long userId) {
        ArticleProcessPort port = processPort.getIfAvailable();
        if (port != null) {
            port.start(article, processKey, userId);
        } else {
            article.adjustStatus(STATUS_PUBLISHED);
            article.setProcessInstanceId(null);
        }
    }

    /**
     * 撤销文章名下所有还在进行的流程实例。按文章ID（流程的业务键）查找，不依赖文章上记着的流程实例ID：
     * 文章改换到没有流程的栏目、栏目的流程被清空之后，文章上的实例ID可能已经对不上，那个实例却还在跑
     * （docs/spec/article-management/plan.md Phase 1.5）
     */
    private void cancelProcesses(Article article, String reason) {
        processPort.ifAvailable(port -> port.cancelAll(article.getId(), article.getSiteId(), reason));
    }

    /**
     * 操作员是否持有该文章正在进行的审核任务。社区版没有审批流程：恒为 {@code false}
     */
    public boolean isReviewer(Article article, User user) {
        ArticleProcessPort port = processPort.getIfAvailable();
        return port != null && port.isReviewer(article, user);
    }

    /**
     * 提交。草稿、待审核、已归档、已退回、已下线、已删除，可以提交。
     * <p>
     * 栏目有审批流程：进入流程（待审核）。已经在这个栏目的流程里等待审核的，无需处理。<br>
     * 栏目没有流程（或社区版）：发布——仍按上下线时间归位，上线时间在将来的是待发布（D4）。<br>
     * 下线时间已过的文章不能提交：提交了也只会是已下线，直接拒绝，而不是什么都没发生却提示成功。
     * <p>
     * 只写状态与流程两组列，不回写整行（docs/spec/article-management/plan.md Phase 1.1）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Article bean, Long userId) {
        short status = bean.getStatus();
        if (status != STATUS_DRAFT && status != STATUS_PENDING && status != STATUS_ARCHIVED
                && status != STATUS_REJECTED && status != STATUS_OFFLINE && status != STATUS_DELETED) {
            return;
        }
        requireOfflineDateAhead(bean);
        String processKey = bean.getChannel().getProcessKey();
        // 栏目有流程、且装有流程引擎（商业版）时才走审批；否则视同无流程
        ArticleProcessPort port = StringUtils.isNotBlank(processKey) ? processPort.getIfAvailable() : null;
        if (port != null && status == STATUS_PENDING && port.isRunning(bean, processKey)) {
            return;
        }
        // 走到这里，文章名下若还有流程实例在跑，都是过时的（改换过栏目、栏目的流程被换掉或清空）：先撤销，
        // 否则旧流程的审核人日后“通过”“退回”，会把这篇文章的状态又改掉
        cancelProcesses(bean, "resubmit");
        if (port != null) {
            port.start(bean, processKey, userId);
        } else {
            bean.adjustStatus(STATUS_PUBLISHED);
            bean.setProcessInstanceId(null);
        }
        // 退回原因只对“已退回”有意义：重新提交即清除，不随文章发布出去
        bean.getExt().setRejectReason(null);
        mapper.updateStatus(bean.getId(), bean.getStatus());
        extMapper.updateProcess(bean.getId(), bean.getProcessInstanceId(), null);
        afterStatusChange(bean.getId());
    }

    /**
     * 提交审核、绝不直接发布（docs/spec/member-contribute/plan.md D6）：会员投稿一律待审核。栏目有审批流程且装有流程引擎时
     * 进入流程（流程端口把状态置为待审核并记下实例ID）；否则停在待审核、无流程实例，由“文章管理”里的提交（通过）或退回处置。
     * {@link #submit} 不适用：栏目没有流程时它会直接发布。
     * <p>
     * 退回原因随重新提交清除；名下过时的流程实例先撤销（同 {@link #submit}）。只写状态与流程两组列
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitForReview(Article bean, Long userId) {
        requireOfflineDateAhead(bean);
        String processKey = bean.getChannel().getProcessKey();
        ArticleProcessPort port = StringUtils.isNotBlank(processKey) ? processPort.getIfAvailable() : null;
        if (port != null && bean.getStatus() == STATUS_PENDING && port.isRunning(bean, processKey)) {
            return;
        }
        cancelProcesses(bean, "resubmit");
        if (port != null) {
            port.start(bean, processKey, userId);
        } else {
            bean.setStatus(STATUS_PENDING);
            bean.setProcessInstanceId(null);
        }
        bean.getExt().setRejectReason(null);
        mapper.updateStatus(bean.getId(), bean.getStatus());
        extMapper.updateProcess(bean.getId(), bean.getProcessInstanceId(), null);
        afterStatusChange(bean.getId());
    }

    /**
     * 下线时间已过的文章不能提交（D4）
     */
    public static void requireOfflineDateAhead(Article bean) {
        OffsetDateTime offlineDate = bean.getOfflineDate();
        if (offlineDate != null && !offlineDate.isAfter(OffsetDateTime.now())) {
            throw new LogicException("error.article.offlineDatePassed", bean.getTitle());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void archive(Article bean) {
        if (bean.getStatus() == STATUS_PUBLISHED) {
            updateStatus(bean, STATUS_ARCHIVED);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void offline(Article bean, User user) {
        short status = bean.getStatus();
        if (status == STATUS_PUBLISHED || status == STATUS_ARCHIVED || status == STATUS_READY
                || status == STATUS_PENDING || status == STATUS_REVIEWING) {
            leaveProcess(bean, user);
            updateStatus(bean, STATUS_OFFLINE);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Article bean, User user) {
        leaveProcess(bean, user);
        updateStatus(bean, STATUS_DELETED);
    }

    /**
     * 从回收站恢复：已删除的文章回到草稿（docs/spec/article-management/plan.md D2）。删除时不记原状态——恢复成已发布
     * 等于绕过审核重新上线，回到草稿后由编辑照常提交。删除不动子行（扩展栏目、推送的行），恢复也不动。
     * 不是已删除状态的文章原样返回
     *
     * @return 是否恢复了
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean restore(Article bean) {
        if (bean.getStatus() != STATUS_DELETED) {
            return false;
        }
        updateStatus(bean, STATUS_DRAFT);
        return true;
    }

    /**
     * 下线、删除时退出审批：撤销还在进行的流程实例，文章上不再记着它的ID。退回原因保留——那是“已退回”阶段留下的记录，
     * 重新提交时才清除
     */
    private void leaveProcess(Article bean, User user) {
        cancelProcesses(bean, user.getName() + ":cancel");
        if (bean.getProcessInstanceId() != null) {
            bean.setProcessInstanceId(null);
            extMapper.updateProcess(bean.getId(), null, bean.getExt().getRejectReason());
        }
    }

    /**
     * 只改状态：列定向写入，不回写整行（docs/spec/article-management/plan.md Phase 1.1）。之后统一收尾：
     * 重建索引、发布监听、统计失效。归档、下线、删除、恢复、审核通过、定时上下线都走这里
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Article bean, short status) {
        bean.setStatus(status);
        mapper.updateStatus(bean.getId(), status);
        afterStatusChange(bean.getId());
    }

    /**
     * 审核退回：状态置为已退回，记下退回原因。流程实例ID保留——作者要从“流程任务”里看审核意见
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Article bean, String reason) {
        bean.setStatus(STATUS_REJECTED);
        bean.getExt().setRejectReason(reason);
        mapper.updateStatus(bean.getId(), STATUS_REJECTED);
        extMapper.updateProcess(bean.getId(), bean.getProcessInstanceId(), reason);
        afterStatusChange(bean.getId());
        rejectListeners.forEach(it -> it.afterArticleReject(bean));
    }

    /**
     * 置顶：只写置顶值与截止时间
     *
     * @param sticky     置顶值，0 为取消置顶
     * @param stickyDate 置顶截止时间，可为 {@code null}
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSticky(Article bean, short sticky, @Nullable OffsetDateTime stickyDate) {
        bean.setSticky(sticky);
        bean.setStickyDate(stickyDate);
        mapper.updateSticky(bean.getId(), sticky, stickyDate);
        afterStatusChange(bean.getId());
    }

    /**
     * 状态、置顶这类写入之后的统一收尾：按库中现值重建全文索引（事务提交后）、触发发布监听、使内容统计失效。
     * <p>
     * 以前这一步靠整行 update 顺带完成，而审核通过、退回、定时上下线走的轻量 update 并不重建索引：审核通过的文章
     * 搜不到，自动下线的文章还搜得到（docs/spec/article-management/plan.md H2）
     */
    private void afterStatusChange(Long id) {
        indexAfterCommit(id);
        Article article = mapper.select(id);
        if (article != null) {
            firePublishListeners(article);
            contentStatEvictor.article(article.getSiteId());
        }
    }

    /**
     * 保存文章的修改并同步扩展栏目（docs/spec/article-data-permission/plan.md D20）。同一事务，先锁住落位家族的源文章
     * （D17 的那把锁：推送、扩展栏目同步、改换栏目三者由此依次进行），锁后重读库中现值。改换了栏目时，新栏目里不得已有
     * 家族的另一成员（源文章或它的子行）——本次保存同时从扩展栏目里去掉的子行除外：操作员把子行所在的栏目改成了主栏目
     * 并取消勾选，由同步删去该子行、源文章顶上。不合格在任何写入之前拒绝；扩展栏目同步在同一事务里，它的拒绝不再
     * 留下已改换栏目的行
     *
     * @param desiredRefers 期望关联为 REFER 的栏目ID列表（{@code null} 表示无变更）
     * @param desiredCopies 期望关联为 COPY  的栏目ID列表（{@code null} 表示无变更）
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Article bean, Long userId, @Nullable List<String> tagNames,
                       @Nullable List<Long> desiredRefers, @Nullable List<Long> desiredCopies, User operator) {
        update(bean, userId, tagNames, desiredRefers, desiredCopies, null, null, operator);
    }

    /**
     * 保存文章的修改，连同扩展栏目、专题、相关文章一起，在一个事务里（docs/spec/article-management/plan.md Phase 1.8）
     *
     * @param tagNames 标签；{@code null} 表示无变更
     * @param topicIds 所属专题；{@code null} 表示无变更
     * @param related  相关文章；{@code null} 表示无变更
     * @return 相关文章里“互相关联”发生变化的对方文章：它们的列表变了，调用方据此重新生成其静态页
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Article> update(Article bean, Long userId, @Nullable List<String> tagNames,
                                @Nullable List<Long> desiredRefers, @Nullable List<Long> desiredCopies,
                                @Nullable List<Long> topicIds, @Nullable List<RelatedItem> related, User operator) {
        // 锁序（D22）：站点行在先，源文章行在后
        siteMapper.lockById(bean.getSiteId());
        Long sourceId = bean.getSrcId() != null ? bean.getSrcId() : bean.getId();
        mapper.lockById(sourceId);
        Article stored = mapper.select(bean.getId());
        if (stored != null && !Objects.equals(stored.getChannelId(), bean.getChannelId())) {
            requireChannelFree(bean, sourceId, desiredRefers, desiredCopies);
        }
        update(bean, userId, tagNames);
        assignExtendedChannels(bean.getId(), desiredRefers, desiredCopies, operator);
        return assignTopicsAndRelated(bean.getId(), topicIds, related);
    }

    /**
     * 改换栏目的判定（D20）：新栏目里不得已有家族的另一成员。家族 = 源文章及 {@code src_id_} 指向它的全部子行
     * （推送、扩展栏目的），不论类型与站点——栏目ID已限定站点
     */
    private void requireChannelFree(Article bean, Long sourceId,
                                    @Nullable List<Long> desiredRefers, @Nullable List<Long> desiredCopies) {
        List<Article> family = new ArrayList<>(mapper.listBySrcId(sourceId));
        if (!sourceId.equals(bean.getId())) {
            Optional.ofNullable(mapper.select(sourceId)).ifPresent(family::add);
        }
        boolean isSource = sourceId.equals(bean.getId());
        for (Article member : family) {
            if (member.getId().equals(bean.getId()) || !Objects.equals(member.getChannelId(), bean.getChannelId())) {
                continue;
            }
            if (isSource && member.getSrcId() != null
                    && droppedByThisSave(member, desiredRefers, desiredCopies)) {
                continue;
            }
            throw new LogicException("error.article.placement.moveCollision");
        }
    }

    /**
     * 该子行是否会被本次保存的扩展栏目同步删去：它是扩展栏目管理的类型（REFER / COPY），而期望的扩展栏目里没有它所在的
     * 栏目（与 {@link #assignExtendedChannels} 同一规则；两个列表都为 {@code null} 即不同步，什么都不删）
     */
    private static boolean droppedByThisSave(Article child, @Nullable List<Long> desiredRefers,
                                             @Nullable List<Long> desiredCopies) {
        if (desiredRefers == null && desiredCopies == null) {
            return false;
        }
        if (child.getType() != TYPE_REFER && child.getType() != TYPE_COPY) {
            return false;
        }
        Set<Long> desiredAll = new HashSet<>();
        if (desiredRefers != null) {
            desiredAll.addAll(desiredRefers);
        }
        if (desiredCopies != null) {
            desiredAll.addAll(desiredCopies);
        }
        return !desiredAll.contains(child.getChannelId());
    }

    /**
     * 批量移动栏目的结果
     *
     * @param movedByOldChannel 已移动的文章，按原栏目ID分组；文章对象上的栏目已是新栏目
     * @param skipped           略过的篇数
     */
    public record MoveResult(Map<Long, List<Article>> movedByOldChannel, int skipped) {
        public int moved() {
            return movedByOldChannel.values().stream().mapToInt(List::size).sum();
        }
    }

    /**
     * 批量移动栏目（docs/spec/article-management/plan.md Phase 3）。只改栏目一列：不动正文、标签、修改时间、修改人。
     * 锁序同其它落位写入（docs/spec/article-data-permission/plan.md D22）：站点行在先，各落位家族的源文章行在后，
     * 按ID升序；锁后重读库中现值再判定。
     * <p>
     * 不合条件的文章略过并计数，不让整批失败——批量操作里混进几篇不能动的是常态：
     * <ul>
     * <li>已在目标栏目；
     * <li>待审核、审核中：审批流程按原栏目的流程发起，换了栏目流程就对不上；
     * <li>在原栏目或目标栏目里属于不可编辑状态（{@link #editableIn}），而操作员没有“全部状态权限”——否则把已发布的文章
     * 移进要审核的栏目，就绕过了那个栏目的审核；
     * <li>目标栏目的文章模型不同：自定义字段会对不上；
     * <li>目标栏目里已有同一落位家族的成员（源文章或它的引用、复制）：同一篇文章在一个栏目里只能出现一次（D20）。
     * </ul>
     *
     * @param articles 待移动的文章，调用方已校验属于本站且在操作员的数据权限之内
     * @param target   目标栏目，调用方已校验属于本站、在操作员的文章权限之内、是末级文章栏目
     */
    @Transactional(rollbackFor = Exception.class)
    public MoveResult move(List<Article> articles, Channel target, User operator) {
        Map<Long, List<Article>> movedByOldChannel = new LinkedHashMap<>();
        if (articles.isEmpty()) {
            return new MoveResult(movedByOldChannel, 0);
        }
        siteMapper.lockById(target.getSiteId());
        Set<Long> sourceIds = new TreeSet<>();
        for (Article article : articles) {
            sourceIds.add(sourceIdOf(article));
        }
        mapper.lockByIds(sourceIds);
        int skipped = 0;
        for (Article passed : articles) {
            Article bean = mapper.select(passed.getId());
            if (bean == null || !movable(bean, target, operator)) {
                skipped++;
                continue;
            }
            Long oldChannelId = bean.getChannelId();
            mapper.updateChannel(bean.getId(), target.getId());
            bean.setChannelId(target.getId());
            bean.setChannel(target);
            afterStatusChange(bean.getId());
            movedByOldChannel.computeIfAbsent(oldChannelId, key -> new ArrayList<>()).add(bean);
        }
        return new MoveResult(movedByOldChannel, skipped);
    }

    private static Long sourceIdOf(Article article) {
        return article.getSrcId() != null ? article.getSrcId() : article.getId();
    }

    private boolean movable(Article bean, Channel target, User operator) {
        if (Objects.equals(bean.getChannelId(), target.getId())) {
            return false;
        }
        short status = bean.getStatus();
        if (status == STATUS_PENDING || status == STATUS_REVIEWING) {
            return false;
        }
        if (!operator.hasAllStatusPermission()
                && !(editableIn(bean.getChannel(), status) && editableIn(target, status))) {
            return false;
        }
        if (!Objects.equals(bean.getChannel().getArticleModelId(), target.getArticleModelId())) {
            return false;
        }
        // 落位家族：源文章及 src_id_ 指向它的全部子行；本批里先移进目标栏目的成员，这里读得到
        Long sourceId = sourceIdOf(bean);
        List<Article> family = new ArrayList<>(mapper.listBySrcId(sourceId));
        if (!sourceId.equals(bean.getId())) {
            Optional.ofNullable(mapper.select(sourceId)).ifPresent(family::add);
        }
        for (Article member : family) {
            if (!member.getId().equals(bean.getId()) && Objects.equals(member.getChannelId(), target.getId())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 该状态的文章在这个栏目里是否可编辑：与 {@link Article#isEditable()} 同一规则，只是栏目可以另行指定——
     * 没有审批流程的栏目都可编辑；有审批流程的栏目里，已发布、已归档、待发布、审核中的文章不可编辑
     */
    public static boolean editableIn(Channel channel, short status) {
        return StringUtils.isBlank(channel.getProcessKey())
                || (status != STATUS_PUBLISHED && status != STATUS_ARCHIVED && status != STATUS_READY
                && status != STATUS_REVIEWING);
    }

    /**
     * 完整的编辑保存。扩展数据只写内容列（{@link ArticleExtMapper#updateContent}）：静态页路径、流程实例ID、退回原因
     * 由静态化与审批流程各自写入，这里不碰——否则会用读出时的旧值盖掉它们期间的更新（D9）
     *
     * @param tagNames 标签；{@code null} 表示无变更
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Article bean, Long userId, @Nullable List<String> tagNames) {
        bean.setModifiedUserId(userId);
        fillSlug(bean);
        bean.setWithImage(StringUtils.isNotBlank(bean.getImage()));
        bean.setModified(OffsetDateTime.now());
        ArticleExt ext = bean.getExt();
        ext.setText(sanitizeService.sanitize(ext.getText(), PolicyTier.RICH));
        Channel channel = Optional.ofNullable(channelService.select(bean.getChannelId())).orElseThrow(() ->
                new IllegalArgumentException(Channel.NOT_FOUND + bean.getChannelId()));
        Model model = channel.getArticleModel();
        bean.disassembleCustoms(model, sanitizeService);
        // 入组文章在覆盖前取库中现值比对内容（净化、拆解之后同口径）：内容未变的保存不算原创修改，
        // 否则新增译文后顺手点“保存”会把刚建的全部译文标为“可能过期”（2026-09-05 实测）；未入组不多查一次
        Article before = articleI18nMapper.select(bean.getId()) != null ? mapper.select(bean.getId()) : null;
        boolean contentChanged = before == null || !bean.isSameContent(before);
        mapper.update(bean);
        extMapper.updateContent(ext);
        if (tagNames != null) {
            tagService.reduceReferByArticleId(bean.getId());
            articleTagMapper.deleteByArticleId(bean.getId());
            insertRelatedList(bean.getId(), bean.getSiteId(), userId, tagNames);
        }
        attachmentService.updateRefer(GeneratedArticle.TABLE_NAME, bean.getId(), bean.getAttachmentUrls(model));
        // 翻译过期锚点：仅此完整编辑保存路径（唯一推进 modified_ 的路径）维护锚点；
        // 轻量重载（工作流通过/退回、归档、下线等）不代表内容修改，保持锚点沉默
        articleI18nAnchorService.trackEditorialSave(bean.getId(), bean.isNormal(), contentChanged, bean.getModified());
        indexAfterCommit(bean.getId());
        firePublishListeners(bean);
        // 发布日期可被改到窗口内外，概览的按日序列随之变化
        contentStatEvictor.article(bean.getSiteId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void moveOrder(Long fromId, Long toId) {
        OrderEntityUtils.move(mapper, fromId, toId);
    }

    @Transactional(rollbackFor = Exception.class)
    public int completelyDelete(Article bean, User user) {
        for (Article dest : bean.getDestList()) {
            // 引用和映射，则直接删除
            if (dest.getType() == TYPE_MAP || dest.getType() == TYPE_REFER) {
                completelyDelete(dest, user);
            }
            // 复制或其它，则清空srcId
            else {
                dest.setSrcId(null);
                mapper.updateSrcIdToNull(dest.getId());
            }
        }
        cancelProcesses(bean, user.getName() + ":cancel");
        deleteListeners.forEach(it -> it.preArticleDelete(bean.getId()));
        attachmentService.deleteRefer(GeneratedArticle.TABLE_NAME, bean.getId());
        extMapper.delete(bean.getId());
        tagService.reduceReferByArticleId(bean.getId());
        articleTagMapper.deleteByArticleId(bean.getId());
        // 专题、相关文章关联行显式清理（外键 CASCADE 仅兜底；相关文章双向：本文的列表与他文列表里的本文）
        articleTopicMapper.deleteByArticleId(bean.getId());
        articleRelatedMapper.deleteByArticleId(bean.getId());
        // 翻译等价集成员清理（multilingual P5）：栏目/站点级联删除均汇入本方法，随行清理
        articleI18nMapper.deleteByArticleId(bean.getId());
        blockItemService.deleteByArticleId(bean.getId());
        int count = mapper.delete(bean.getId());
        indexAfterCommit(bean.getId());
        htmlService.deleteArticleHtml(bean);
        contentStatEvictor.article(bean.getSiteId());
        return count;
    }

    /**
     * 对源文章加行锁，用于写入其落位之前（docs/spec/article-data-permission/plan.md D17）。必须在调用方已开启的
     * 事务里执行（{@link Propagation#MANDATORY}），且应是该事务里针对该文章的第一次读——REPEATABLE READ 下
     * 快照从事务的第一次普通读建立，先读后锁就看不到等待期间别的事务已提交的子行。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockSource(Long articleId) {
        lockArticle(articleId);
    }

    /**
     * 对文章行加行锁（{@code select … for update}），同 {@link #lockSource} 的约束：调用方已开事务，且是该事务里对
     * 这篇文章的第一次读。会员投稿的修改、删除在校验归属与状态前先锁行（docs/spec/member-contribute/plan.md D8）：
     * 校验时还“可修改”、写回时已被编辑发布的话，全行更新会把刚发布的文章改回待审核
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockArticle(Long articleId) {
        mapper.lockById(articleId);
    }

    /**
     * 全量同步扩展栏目（REFER / COPY 子行）：参数代表期望的最终状态，差异由本方法计算。
     * <p>
     * 每个新增子行作为独立的 ujcms_article 行通过现有 {@link #insert(Article, Long, List)} 路径写入，
     * 因此目标栏目的审批工作流（processKey）自然触发；从期望集合中移除的子行通过
     * {@link #completelyDelete} 硬删（与"取消勾选 = 真删"的管理 UX 契合）。
     * 栏目类型在 REFER 与 COPY 之间切换会走 delete-then-create 路径：
     * 旧子行的 id_ 与其 ujcms_workflow 实例一并销毁，新子行重新进入目标栏目的初审队列。
     * 这是有意选择——hot-swap UPDATE 会绕过新目标栏目的审核，与"REFER/COPY = 一次发布行为"
     * 的语义冲突。
     *
     * @param articleId      源文章ID
     * @param desiredRefers  期望关联为 REFER 的栏目ID列表（{@code null} 表示无变更）
     * @param desiredCopies  期望关联为 COPY  的栏目ID列表（{@code null} 表示无变更）
     * @param operator       操作用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignExtendedChannels(Long articleId,
                                       @Nullable List<Long> desiredRefers,
                                       @Nullable List<Long> desiredCopies,
                                       User operator) {
        if (desiredRefers == null && desiredCopies == null) {
            return;
        }
        List<Long> refers = desiredRefers != null ? desiredRefers : Collections.emptyList();
        List<Long> copies = desiredCopies != null ? desiredCopies : Collections.emptyList();

        // 先锁源文章，再读它的子行（docs/spec/article-data-permission/plan.md D17）：一次保存发两遍时，
        // 第二遍要等第一遍提交后才读得到它新建的子行，否则两遍都新建、同一栏目落两行。
        // 调用方已先锁住站点行（D22 的锁序：{@link #insert}、{@link #update} 的六参数重载）
        mapper.lockById(articleId);
        Article src = mapper.select(articleId);
        if (src == null) {
            throw new LogicException("error.article.notFound");
        }

        Map<Long, Article> currentByChannelId = listExtendedChildren(articleId, src.getSiteId());

        Set<Long> desiredReferSet = new HashSet<>(refers);
        Set<Long> desiredCopySet = new HashSet<>(copies);
        Set<Long> desiredAll = new HashSet<>();
        desiredAll.addAll(desiredReferSet);
        desiredAll.addAll(desiredCopySet);
        for (Long channelId : desiredAll) {
            if (desiredReferSet.contains(channelId) && desiredCopySet.contains(channelId)) {
                throw new LogicException("error.article.placement.collision");
            }
            if (Objects.equals(channelId, src.getChannelId())) {
                throw new LogicException("error.article.placement.collision");
            }
        }

        List<Long> staleChannelIds = new ArrayList<>();
        for (Map.Entry<Long, Article> entry : currentByChannelId.entrySet()) {
            Long channelId = entry.getKey();
            Article child = entry.getValue();
            boolean staleType = (child.getType() == TYPE_REFER && desiredCopySet.contains(channelId))
                    || (child.getType() == TYPE_COPY && desiredReferSet.contains(channelId));
            if (!desiredAll.contains(channelId) || staleType) {
                staleChannelIds.add(channelId);
            }
        }
        for (Long channelId : staleChannelIds) {
            Article child = currentByChannelId.remove(channelId);
            if (child != null) {
                completelyDelete(child, operator);
            }
        }

        Set<Long> occupiedChannelIds = new HashSet<>(currentByChannelId.keySet());
        occupiedChannelIds.add(src.getChannelId());

        addExtendedChildrenOfType(src, refers, currentByChannelId, occupiedChannelIds, operator, TYPE_REFER);
        addExtendedChildrenOfType(src, copies, currentByChannelId, occupiedChannelIds, operator, TYPE_COPY);
    }

    /**
     * 文章现有的扩展栏目子行（REFER / COPY），按所在栏目索引。
     * <p>
     * 只认源文章所在站点的子行（docs/spec/article-data-permission/plan.md D15）：扩展栏目是本站之内的事，
     * 站群推送到其它站点的子行不归这里管——把它们算进来，保存源文章时只要没把对方站点的栏目原样提交回来，
     * 对方站点的那篇文章就被当作“取消勾选”硬删了。站内推送产生的子行与扩展栏目的子行是一回事，照常计入
     *
     * @param siteId 源文章所在的站点
     */
    private Map<Long, Article> listExtendedChildren(Long articleId, Long siteId) {
        Map<Long, Article> currentByChannelId = new HashMap<>();
        for (Article child : mapper.listBySrcId(articleId)) {
            boolean extended = child.getType() == TYPE_REFER || child.getType() == TYPE_COPY;
            if (extended && Objects.equals(child.getSiteId(), siteId)) {
                currentByChannelId.put(child.getChannelId(), child);
            }
        }
        return currentByChannelId;
    }

    /**
     * {@link #assignExtendedChannels} 将会新建子行的栏目：期望的扩展栏目里，现在还没有同类型子行的那些
     * （REFER 与 COPY 互换也算新建）。数据权限只校验这一部分——原样提交回来的扩展栏目不再判定
     *
     * @param articleId 源文章ID。新增文章时为 {@code null}：期望的扩展栏目全部是新建
     * @param siteId    源文章所在的站点
     */
    public Set<Long> listAddedExtendedChannelIds(@Nullable Long articleId, Long siteId,
                                                 @Nullable List<Long> desiredRefers,
                                                 @Nullable List<Long> desiredCopies) {
        if (desiredRefers == null && desiredCopies == null) {
            return Collections.emptySet();
        }
        Map<Long, Article> current = articleId != null
                ? listExtendedChildren(articleId, siteId) : Collections.emptyMap();
        Set<Long> added = new LinkedHashSet<>();
        collectAddedExtendedChannelIds(added, desiredRefers, current, TYPE_REFER);
        collectAddedExtendedChannelIds(added, desiredCopies, current, TYPE_COPY);
        return added;
    }

    private static void collectAddedExtendedChannelIds(Set<Long> added, @Nullable List<Long> desired,
                                                       Map<Long, Article> current, short type) {
        if (desired == null) {
            return;
        }
        for (Long channelId : desired) {
            Article existing = current.get(channelId);
            if (existing == null || existing.getType() != type) {
                added.add(channelId);
            }
        }
    }

    private void addExtendedChildrenOfType(Article src, List<Long> targetChannelIds,
                                           Map<Long, Article> currentByChannelId,
                                           Set<Long> occupiedChannelIds, User operator, short type) {
        for (Long targetChannelId : targetChannelIds) {
            Article existing = currentByChannelId.get(targetChannelId);
            if (existing != null && existing.getType() == type) {
                continue;
            }
            validateExtendedTarget(src, targetChannelId, occupiedChannelIds);
            Channel target = channelService.select(targetChannelId);
            insertExtendedChild(src, target, operator, type);
            occupiedChannelIds.add(targetChannelId);
        }
    }

    private void validateExtendedTarget(Article src, Long targetChannelId, Set<Long> occupiedChannelIds) {
        if (occupiedChannelIds.contains(targetChannelId)) {
            throw new LogicException("error.article.placement.collision");
        }
        Channel target = channelService.select(targetChannelId);
        if (target == null) {
            throw new LogicException("error.channel.notFound");
        }
        if (!Objects.equals(target.getSiteId(), src.getSiteId())) {
            throw new LogicException("error.article.placement.crossSite");
        }
    }

    private void insertExtendedChild(Article src, Channel target, User operator, short type) {
        Article child = new Article();
        child.setType(type);
        child.setSrcId(src.getId());
        child.setChannelId(target.getId());
        child.setSiteId(src.getSiteId());
        child.setOrgId(src.getOrgId());
        child.setStatus(Article.STATUS_PUBLISHED);

        // 子行是另一篇文章：内容照搬，源文章自己的静态页路径、流程实例、退回原因、各项计数不带过来（Phase 1.3）
        ArticleExt srcExt = extMapper.select(src.getId());
        child.setExt(srcExt != null ? srcExt.copyForChild() : new ArticleExt());
        child.setImage(src.getImage());

        insert(child, operator.getId(), Collections.emptyList());
    }

    /**
     * 同步文章所属的专题（{@code ujcms_article_topic}）：按目标专题集合差量替换关联。
     * 校验通过 {@link TopicService}（Topic 是独立实体，不再借用 Channel 命名空间）。
     *
     * @param articleId 文章ID
     * @param topicIds  目标专题ID列表，按调用方顺序作为初始排序
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignToTopics(Long articleId, @Nullable List<Long> topicIds) {
        List<Long> targets = topicIds != null ? topicIds : Collections.emptyList();
        Article src = mapper.select(articleId);
        if (src == null) {
            throw new LogicException("error.article.notFound");
        }
        for (Long topicId : targets) {
            Topic topic = topicService.select(topicId);
            if (topic == null) {
                throw new LogicException("error.topic.notFound");
            }
            if (!Objects.equals(topic.getSiteId(), src.getSiteId())) {
                throw new LogicException("error.article.topic.crossSite");
            }
        }
        articleTopicMapper.deleteByArticleId(articleId);
        if (targets.isEmpty()) {
            return;
        }
        List<ArticleTopic> rows = new ArrayList<>(targets.size());
        for (int i = 0; i < targets.size(); i++) {
            rows.add(new ArticleTopic(articleId, targets.get(i), i * 10000));
        }
        articleTopicMapper.insertAll(rows);
    }

    /**
     * 反向同步：以专题为锚点重新绑定一组文章。{@code orders} 与 {@code articleIds} 等长，
     * 缺省时按下标 ×10000 生成。
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignArticlesToTopic(Long topicId,
                                      @Nullable List<Long> articleIds,
                                      @Nullable List<Integer> orders) {
        List<Long> ids = articleIds != null ? articleIds : Collections.emptyList();
        Topic topic = topicService.select(topicId);
        if (topic == null) {
            throw new LogicException("error.topic.notFound");
        }
        for (Long articleId : ids) {
            Article article = mapper.select(articleId);
            if (article == null) {
                throw new LogicException("error.article.notFound");
            }
            if (!Objects.equals(article.getSiteId(), topic.getSiteId())) {
                throw new LogicException("error.article.topic.crossSite");
            }
        }
        articleTopicMapper.deleteByTopicId(topicId);
        if (ids.isEmpty()) {
            return;
        }
        List<ArticleTopic> rows = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            int order = orders != null && i < orders.size() && orders.get(i) != null
                    ? orders.get(i) : i * 10000;
            rows.add(new ArticleTopic(ids.get(i), topicId, order));
        }
        articleTopicMapper.insertAll(rows);
    }

    /**
     * 详情读取，附带扩展栏目和专题列表。列表 API 仍使用 {@link #select} 避免 N+1。
     */
    @Nullable
    public Article selectWithChannelListAndTopicList(Long id) {
        Article article = mapper.select(id);
        if (article == null) {
            return null;
        }
        article.setChannelList(mapper.listChannelsBySrcId(id));
        article.setTopicList(articleTopicMapper.listTopicsByArticleId(id));
        // 相关文章：不限状态（编辑要看到已下线/回收站的行并自行决定）；被关联同理
        article.setRelatedList(articleRelatedMapper.listRelatedArticles(id, null));
        article.setIncomingList(articleRelatedMapper.listIncomingArticles(id, null));
        return article;
    }

    /**
     * 相关文章手选上限。列表是"编辑推荐"，不是第二个栏目
     */
    public static final int RELATED_MAX = 20;

    /**
     * 重绑文章的相关文章列表（docs/spec/article-related/plan.md §2）。整体替换出向行（顺序即调用方顺序），
     * 再按 {@code mutual} 同步反向行：为 true 时确保目标文章的列表末尾有本文，为 false 时移除。
     * 同站校验与专题一致；自关联、超出 {@link #RELATED_MAX} 拒绝。
     *
     * @param articleId 文章ID
     * @param items     目标列表；{@code null} 视为清空
     * @return 反向行发生变化的目标文章（调用方据此重生成它们的静态页）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Article> assignRelated(Long articleId, @Nullable List<RelatedItem> items) {
        Article src = mapper.select(articleId);
        if (src == null) {
            throw new LogicException("error.article.notFound");
        }
        Map<Long, RelatedItem> targets = new LinkedHashMap<>();
        for (RelatedItem item : items != null ? items : Collections.<RelatedItem>emptyList()) {
            if (item.getId() != null) {
                targets.putIfAbsent(item.getId(), item);
            }
        }
        if (targets.size() > RELATED_MAX) {
            throw new LogicException("error.article.related.tooMany", String.valueOf(RELATED_MAX));
        }
        Map<Long, Article> resolved = new LinkedHashMap<>();
        for (Long targetId : targets.keySet()) {
            if (Objects.equals(targetId, articleId)) {
                throw new LogicException("error.article.related.self");
            }
            Article target = mapper.select(targetId);
            if (target == null) {
                throw new LogicException("error.article.notFound");
            }
            if (!Objects.equals(target.getSiteId(), src.getSiteId())) {
                throw new LogicException("error.article.related.crossSite");
            }
            resolved.put(targetId, target);
        }
        articleRelatedMapper.deleteOutgoing(articleId);
        if (!resolved.isEmpty()) {
            List<ArticleRelated> rows = new ArrayList<>(resolved.size());
            int i = 0;
            for (Long targetId : resolved.keySet()) {
                rows.add(new ArticleRelated(articleId, targetId, i++ * 10000));
            }
            articleRelatedMapper.insertAll(rows);
        }
        List<Article> changed = new ArrayList<>();
        for (Map.Entry<Long, Article> entry : resolved.entrySet()) {
            Long targetId = entry.getKey();
            boolean exists = articleRelatedMapper.exists(targetId, articleId) > 0;
            boolean mutual = targets.get(targetId).isMutual();
            if (mutual && !exists) {
                Integer max = articleRelatedMapper.maxOrderByArticleId(targetId);
                int order = max == null ? 0 : max + 10000;
                articleRelatedMapper.insert(new ArticleRelated(targetId, articleId, order));
                changed.add(entry.getValue());
            } else if (!mutual && exists) {
                articleRelatedMapper.delete(targetId, articleId);
                changed.add(entry.getValue());
            }
        }
        return changed;
    }

    /**
     * 前台可见的手选相关文章（已发布/已归档，按 order_）。回收站、已下线等在此被过滤，行本身保留，恢复即回来
     */
    public List<Article> listRelated(Long articleId) {
        return articleRelatedMapper.listRelatedArticles(articleId, Arrays.asList(STATUS_PUBLISHED, STATUS_ARCHIVED));
    }

    /**
     * 分页查询某专题下的已发布/已归档文章，按 {@code ujcms_article_topic.order_} 升序。
     */
    public Page<Article> selectPageByTopicId(Long topicId, int page, int pageSize) {
        try (Page<Article> startPage = PageMethod.startPage(page, pageSize)) {
            return startPage.doSelectPage(() -> mapper.listByTopicId(topicId,
                    Arrays.asList(STATUS_PUBLISHED, STATUS_ARCHIVED)));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Article> listAndUpdateStickyDate() {
        OffsetDateTime now = OffsetDateTime.now();
        ArticleArgs args = ArticleArgs.of()
                .geSticky((short) 1)
                .leStickyDate(now);
        List<Article> articles = selectList(args);
        // 返回的对象与库中一致：置顶已到期。调用方若拿着读出时的旧值再写回去，到期的置顶就又回来了（H1）
        for (Article article : articles) {
            article.setSticky((short) 0);
            article.setStickyDate(null);
        }
        mapper.updateStickyDate(now);
        return articles;
    }

    /**
     * 定时任务的一轮：置顶到期、到点上线、到点下线。三步都是定向的批量 SQL；之后逐篇收尾（重建索引、发布监听、
     * 统计失效），不再把读出的整行写回去。
     *
     * @return 本轮状态或置顶发生变化的文章，供调用方重新生成静态页
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Article> applySchedule() {
        List<Article> articles = new ArrayList<>(listAndUpdateStickyDate());
        articles.addAll(listAndUpdateOnlineStatus());
        articles.addAll(listAndUpdateOfflineStatus());
        Set<Long> done = new HashSet<>();
        for (Article article : articles) {
            if (done.add(article.getId())) {
                afterStatusChange(article.getId());
            }
        }
        return articles;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Article> listAndUpdateOnlineStatus() {
        OffsetDateTime now = OffsetDateTime.now();
        ArticleArgs args = ArticleArgs.of()
                .status(Collections.singleton(STATUS_READY))
                .leOnlineDateOrNull(now);
        List<Article> articles = selectList(args);
        for (Article article : articles) {
            article.setStatus(STATUS_PUBLISHED);
        }
        mapper.updateOnlineStatus(now, STATUS_READY, STATUS_PUBLISHED);
        return articles;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Article> listAndUpdateOfflineStatus() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Short> normals = Arrays.asList(STATUS_PUBLISHED, STATUS_ARCHIVED);
        ArticleArgs args = ArticleArgs.of()
                .status(normals)
                .leOfflineDate(now);
        List<Article> articles = selectList(args);
        for (Article article : articles) {
            article.setStatus(STATUS_OFFLINE);
        }
        mapper.updateOfflineStatus(now, normals, STATUS_OFFLINE);
        return articles;
    }

    @Nullable
    public Article select(Long id) {
        return mapper.select(id);
    }

    public List<Article> selectList(ArticleArgs args) {
        QueryInfo queryInfo = QueryParser.parse(withIdTiebreak(args.getQueryMap()), GeneratedArticle.TABLE_NAME,
                "order_desc,id_desc");
        return mapper.selectAll(queryInfo, args.getChannelAncestorIds(), args.getOrgIds(), args.getArticleRoleIds(),
                args.getArticleOrgIds(), args.getOrgRoleIds(), args.getOrgPermIds());
    }

    /**
     * 调用方自定排序时，末尾补上按 ID 排序：排序列有重复值（同一栏目、同一状态、同一发布日期……）而没有唯一的收尾，
     * 数据库每次返回的顺序可以不同，翻页时同一行会出现两次或一次都不出现
     */
    private static Map<String, Object> withIdTiebreak(Map<String, Object> queryMap) {
        if (!(queryMap.get(QueryParser.ORDER_BY) instanceof String orderBy) || StringUtils.isBlank(orderBy)) {
            return queryMap;
        }
        String[] items = StringUtils.split(orderBy, ',');
        if (items.length == 0) {
            // 只有逗号的排序串没有任何排序项，原样交给解析器（不排序），而不是在这里越界
            return queryMap;
        }
        String last = items[items.length - 1].trim();
        if (last.equals("id") || last.startsWith("id_")) {
            return queryMap;
        }
        Map<String, Object> copy = new LinkedHashMap<>(queryMap);
        copy.put(QueryParser.ORDER_BY, orderBy + (last.endsWith("_desc") ? ",id_desc" : ",id"));
        return copy;
    }

    public long count(ArticleArgs args) {
        return PageMethod.count(() -> selectList(args));
    }

    /**
     * 只取主键，条件与排序同 {@link #selectList(ArticleArgs)}。文章导出用：按列表的顺序分批取 ID，再按 ID 取导出用的行，
     * 不为几万行各自加载整张对象图
     */
    public List<Long> selectIds(ArticleArgs args, int offset, int limit) {
        QueryInfo queryInfo = QueryParser.parse(withIdTiebreak(args.getQueryMap()), GeneratedArticle.TABLE_NAME,
                "order_desc,id_desc");
        try (Page<Long> ignored = PageMethod.offsetPage(offset, limit, false)) {
            return mapper.selectIds(queryInfo, args.getChannelAncestorIds(), args.getOrgIds(),
                    args.getArticleRoleIds(), args.getArticleOrgIds(), args.getOrgRoleIds(), args.getOrgPermIds());
        }
    }

    public List<ArticleExportRow> listForExport(Collection<Long> ids) {
        return ids.isEmpty() ? Collections.emptyList() : mapper.listForExport(ids);
    }

    /**
     * 这些标题里，本站已有文章用了的那些（回收站里的、推送与扩展栏目生成的子行不算）。分批查询，标题再多也不会拼出过长的语句
     */
    public Set<String> listExistingTitles(Long siteId, Collection<String> titles) {
        Set<String> existing = new HashSet<>();
        List<String> batch = new ArrayList<>(TITLE_BATCH);
        for (String title : titles) {
            batch.add(title);
            if (batch.size() == TITLE_BATCH) {
                existing.addAll(mapper.listExistingTitles(siteId, batch));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            existing.addAll(mapper.listExistingTitles(siteId, batch));
        }
        return existing;
    }

    private static final int TITLE_BATCH = 500;

    /**
     * 给一页文章一次配齐 TAG 与区块项（docs/spec/article-management/plan.md Phase 6）：各用一条语句取出整页的，
     * 代替序列化时逐篇懒加载——20 行的一页由此少四十来条语句。给属性赋了值，MyBatis 就不再为它懒加载。
     * <p>
     * 只供返回 JSON 列表的接口使用：配上的 TAG 只有 ID 与名称，区块项只有列表要显示的几列，正是 JSON 输出的那些。
     * 模板指令照旧懒加载——模板不一定用得到它们，用到了要的也是完整的对象。
     */
    public void attachListRelations(Collection<Article> articles) {
        if (articles.isEmpty()) {
            return;
        }
        List<Long> ids = articles.stream().map(Article::getId).toList();
        Map<Long, List<Tag>> tags = new HashMap<>(ids.size() * 2);
        for (ArticleTagName row : mapper.listTagNames(ids)) {
            Tag tag = new Tag();
            tag.setId(row.getTagId());
            tag.setName(row.getName());
            tags.computeIfAbsent(row.getArticleId(), key -> new ArrayList<>()).add(tag);
        }
        Map<Long, List<BlockItem>> blockItems = new HashMap<>(ids.size() * 2);
        for (BlockItem item : blockItemService.listByArticleIds(ids)) {
            blockItems.computeIfAbsent(item.getArticleId(), key -> new ArrayList<>()).add(item);
        }
        for (Article article : articles) {
            article.setTags(tags.getOrDefault(article.getId(), new ArrayList<>()));
            article.setBlockItemList(blockItems.getOrDefault(article.getId(), Collections.emptyList()));
        }
    }

    public List<ArticleTagName> listTagNames(Collection<Long> ids) {
        return ids.isEmpty() ? Collections.emptyList() : mapper.listTagNames(ids);
    }

    /**
     * 各状态的文章数：条件与 {@link #selectList(ArticleArgs)} 相同，按状态汇总。排序与计数无关，先去掉——
     * 按关联表的字段排序会平白多关联一张表
     *
     * @return 状态 → 文章数，只含有文章的状态
     */
    public Map<Short, Integer> countByStatus(ArticleArgs args) {
        Map<String, Object> queryMap = new LinkedHashMap<>(args.getQueryMap());
        queryMap.remove(QueryParser.ORDER_BY);
        QueryInfo queryInfo = QueryParser.parse(queryMap, GeneratedArticle.TABLE_NAME);
        Map<Short, Integer> counts = new TreeMap<>();
        for (ArticleStatusCount row : mapper.countByStatus(queryInfo, args.getChannelAncestorIds(), args.getOrgIds(),
                args.getArticleRoleIds(), args.getArticleOrgIds(), args.getOrgRoleIds(), args.getOrgPermIds())) {
            counts.put(row.getStatus(), row.getTotal());
        }
        return counts;
    }

    public List<Article> selectList(ArticleArgs args, int offset, int limit) {
        try (Page<Article> ignored = PageMethod.offsetPage(offset, limit, false)) {
            // 使用 doSelectPage(() -> selectList(args)) 会导致 SystemCoreBootstrap.initLucene 无法获取到数据（其它情况下没有问题）
            // 因为此时 PageHelper 还未初始化
            return selectList(args);
        }
    }

    public Page<Article> selectPage(ArticleArgs args, int page, int pageSize) {
        try (Page<Article> startPage = PageMethod.startPage(page, pageSize)) {
            return startPage.doSelectPage(() -> selectList(args));
        }
    }

    public List<Article> listByIds(Collection<Long> ids) {
        return mapper.listByIds(ids);
    }

    public List<Article> selectByMinId(Long minId, @Nullable Long siteId, @Nullable Long subChannelId,
                                       int offset, int limit) {
        ArticleArgs args = ArticleArgs.of().siteId(siteId).channelAncestorId(subChannelId).minId(minId)
                .status(Arrays.asList(STATUS_PUBLISHED, STATUS_ARCHIVED)).orderById();
        return selectList(args, offset, limit);
    }

    /**
     * sitemap 分页文章列表（docs/spec/sitemap/plan.md D1/D2）：可收录条件在 SQL 内过滤，按 id_ 升序 offset 分页。
     *
     * @param siteId   站点ID
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 本页文章（超出范围为空列表）
     */
    public List<Article> listForSitemap(Long siteId, int page, int pageSize) {
        return PageMethod.offsetPage((page - 1) * pageSize, pageSize, false)
                .doSelectPage(() -> mapper.listBySiteIdForSitemap(siteId, SITEMAP_STATUS));
    }

    /**
     * 站点全部可收录文章的修改时间，按 id_ 升序（与 {@link #listForSitemap} 同序）。sitemap 索引据此算页数与每页 lastmod
     */
    public List<OffsetDateTime> listModifiedForSitemap(Long siteId) {
        return mapper.listModifiedForSitemap(siteId, SITEMAP_STATUS);
    }

    /**
     * 各栏目（含祖先栏目）子树内最新可收录文章的修改时间：栏目ID → 修改时间
     */
    public Map<Long, OffsetDateTime> listChannelModifiedForSitemap(Long siteId) {
        Map<Long, OffsetDateTime> map = new HashMap<>(64);
        for (Channel holder : mapper.listChannelModifiedForSitemap(siteId, SITEMAP_STATUS)) {
            if (holder.getId() != null && holder.getModified() != null) {
                map.put(holder.getId(), holder.getModified());
            }
        }
        return map;
    }

    @Nullable
    public Article findNext(Long id, Long order, Long channelId) {
        List<Article> list = PageMethod.offsetPage(0, 1, false).doSelectPage(() ->
                mapper.findNext(id, order, channelId, Arrays.asList(STATUS_PUBLISHED, STATUS_ARCHIVED)));
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    @Nullable
    public Article findPrev(Long id, Long order, Long channelId) {
        List<Article> list = PageMethod.offsetPage(0, 1, false).doSelectPage(() ->
                mapper.findPrev(id, order, channelId, Arrays.asList(STATUS_PUBLISHED, STATUS_ARCHIVED)));
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public List<EsArticle> titleSimilarity(double similarity, String title, Long siteId, @Nullable Long excludeId) {
        ArticleLuceneArgs args = new ArticleLuceneArgs();
        args.setTitle(title);
        args.setSiteId(siteId);
        if (excludeId != null) {
            args.setExcludeIds(Collections.singletonList(excludeId));
        }
        // 获取得分最高的10条记录
        List<EsArticle> list = articleLucene.findAll(args, null, new OffsetLimitRequest(0, 10)).getContent();
        List<EsArticle> result = new ArrayList<>();
        JaroWinkler jaroWinkler = new JaroWinkler();
        for (EsArticle bean : list) {
            double curr = jaroWinkler.similarity(title, bean.getTitle());
            // 返回相似度大于 60% 的数据
            if (curr > similarity) {
                bean.setScore(curr);
                result.add(bean);
            }
        }
        // 相似度高的在前
        result.sort(Comparator.comparingDouble(EsArticle::getScore).reversed());
        return result;
    }

    /**
     * 统计文章数量
     *
     * @param siteId      站点ID
     * @param publishDate 发布日期
     * @param status      状态
     * @return 文章数量
     */
    public int countByPublishDate(Long siteId, @Nullable OffsetDateTime publishDate, Collection<Short> status) {
        return mapper.countByPublishDate(siteId, publishDate, status);
    }

    public boolean existsByChannelId(Long channelId) {
        return mapper.existsByChannelId(channelId) > 0;
    }

    public boolean existsByUserId(Long userId) {
        return mapper.existsByUserId(userId) > 0;
    }

    public boolean existsByTitleAndSiteId(String title, Long siteId) {
        return mapper.existsByTitleAndSiteId(title, siteId) > 0;
    }

    @Override
    public void preUserDelete(Long userId) {
        if (existsByUserId(userId)) {
            throw new LogicException("error.refer.article");
        }
        mapper.updateModifiedUser(userId, User.ANONYMOUS_ID);
    }

    @Override
    public void preChannelDelete(Long channelId) {
        if (existsByChannelId(channelId)) {
            throw new LogicException("error.refer.article");
        }
    }

    @Override
    public void preSiteDelete(Long siteId) {
        extMapper.deleteBySiteId(siteId);
        articleTagMapper.deleteBySiteId(siteId);
        articleI18nMapper.deleteBySiteId(siteId);
        articleRelatedMapper.deleteBySiteId(siteId);
        mapper.updateSrcIdToNullBySiteId(siteId);
        mapper.deleteBySiteId(siteId);
        // 文章行是整批 SQL 删除的，不经过 completelyDelete：站点的全文索引在此一并清掉，否则跨站点搜索还搜得到已删站点的文章。
        // 索引的删除不随事务回滚，所以等提交之后再做：站点删除在后面的步骤失败回滚时，站点还在，索引也还在
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        articleLucene.deleteBySiteId(siteId);
                    } catch (RuntimeException e) {
                        logger.error("site {}: full-text index not cleared after commit", siteId, e);
                    }
                }
            });
        } else {
            articleLucene.deleteBySiteId(siteId);
        }
        contentStatEvictor.article(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        // 栏目(100), 区块(200), **文章(300)**、区块项(400)
        return 300;
    }

    /**
     * 事务提交之后，按库中现值重建这篇文章的全文索引：文章还在就更新，不在了就删除。同一事务里多次要求只做一次。
     * <p>
     * 全文索引的写入是立即生效的，不随数据库事务回滚。以前在事务里边写库边写索引，之后的步骤一旦失败回滚
     * （扩展栏目被拒绝、落位冲突……），索引里就留下一篇库里没有的文章，或者少了一篇还在的
     * （docs/spec/article-management/plan.md Phase 1.7）。不在事务里时立即执行，失败照常抛出
     */
    private void indexAfterCommit(Long id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            reindex(id);
            return;
        }
        @SuppressWarnings("unchecked")
        Set<Long> pending = (Set<Long>) TransactionSynchronizationManager.getResource(INDEX_PENDING);
        if (pending != null) {
            pending.add(id);
            return;
        }
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(id);
        TransactionSynchronizationManager.bindResource(INDEX_PENDING, ids);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 数据已经提交。索引写失败（磁盘、Elasticsearch 不可用）只记日志，不向外抛：抛出去调用方会把已保存的
                // 文章当作失败（重试就多出一篇），排在后面的提交后动作（搜索引擎推送、发布通知）也不再执行。
                // 索引可用"全文索引重建"补回
                for (Long articleId : ids) {
                    try {
                        reindex(articleId);
                    } catch (RuntimeException e) {
                        logger.error("article {}: full-text index not updated after commit", articleId, e);
                    }
                }
            }

            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(INDEX_PENDING);
            }
        });
    }

    private void reindex(Long id) {
        Article article = mapper.select(id);
        if (article != null) {
            articleLucene.update(EsArticle.of(article));
        } else {
            articleLucene.deleteById(id);
        }
    }

    /**
     * 当前事务里等待重建索引的文章ID（事务同步资源的键）
     */
    private static final Object INDEX_PENDING = new Object();

    private void firePublishListeners(Article bean) {
        if (bean.getStatus() == STATUS_PUBLISHED) {
            publishListeners.forEach(it -> it.afterArticlePublish(bean));
        }
    }

    private List<ArticleDeleteListener> deleteListeners = Collections.emptyList();

    @Lazy
    @Autowired(required = false)
    public void setDeleteListeners(List<ArticleDeleteListener> deleteListeners) {
        this.deleteListeners = deleteListeners;
    }

    private List<ArticlePublishListener> publishListeners = Collections.emptyList();

    @Lazy
    @Autowired(required = false)
    public void setPublishListeners(List<ArticlePublishListener> publishListeners) {
        this.publishListeners = publishListeners;
    }

    private List<ArticleRejectListener> rejectListeners = Collections.emptyList();

    @Lazy
    @Autowired(required = false)
    public void setRejectListeners(List<ArticleRejectListener> rejectListeners) {
        this.rejectListeners = rejectListeners;
    }

    /**
     * sitemap 可收录状态：已发布、已归档（docs/spec/sitemap/plan.md D1）
     */
    private static final List<Short> SITEMAP_STATUS = List.of(STATUS_PUBLISHED, STATUS_ARCHIVED);
}
