package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.google.common.collect.ImmutableMap;
import com.ujcms.cms.core.domain.*;
import com.ujcms.cms.core.generator.HtmlService;
import com.ujcms.cms.core.listener.ChannelDeleteListener;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.lucene.domain.EsArticle;
import com.ujcms.cms.core.mapper.*;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.util.query.CustomFieldQuery;
import com.ujcms.util.query.QueryInfo;
import com.ujcms.util.query.QueryParser;
import com.ujcms.util.web.exception.LogicException;
import org.apache.commons.lang3.StringUtils;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.owasp.html.PolicyFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final HtmlService htmlService;
    private final PolicyFactory policyFactory;
    private final RuntimeService runtimeService;
    private final ChannelService channelService;
    private final TagService tagService;
    private final ArticleLucene articleLucene;
    private final ArticleMapper mapper;
    private final ArticleExtMapper extMapper;
    private final ArticleBufferMapper bufferMapper;
    private final ArticleCustomMapper customMapper;
    private final ArticleTagMapper articleTagMapper;
    private final ArticleStatMapper statMapper;
    private final AttachmentService attachmentService;
    private final SeqService seqService;

    public ArticleService(HtmlService htmlService, PolicyFactory policyFactory,
                          RuntimeService runtimeService, ChannelService channelService,
                          TagService tagService, ArticleLucene articleLucene,
                          ArticleMapper mapper, ArticleExtMapper extMapper, ArticleBufferMapper bufferMapper,
                          ArticleCustomMapper customMapper, ArticleTagMapper articleTagMapper,
                          ArticleStatMapper statMapper, AttachmentService attachmentService, SeqService seqService) {
        this.htmlService = htmlService;
        this.policyFactory = policyFactory;
        this.runtimeService = runtimeService;
        this.channelService = channelService;
        this.tagService = tagService;
        this.articleLucene = articleLucene;
        this.mapper = mapper;
        this.extMapper = extMapper;
        this.bufferMapper = bufferMapper;
        this.customMapper = customMapper;
        this.articleTagMapper = articleTagMapper;
        this.statMapper = statMapper;
        this.attachmentService = attachmentService;
        this.seqService = seqService;
    }

    private void insertRelatedList(Integer articleId, Integer siteId, Integer userId,
                                   List<String> tagNames, List<ArticleCustom> customList) {
        short tagOrder = 0;
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
        customList.forEach(it -> {
            it.setId(seqService.getNextValLong(ArticleCustom.TABLE_NAME));
            it.setArticleId(articleId);
            if (it.isRichEditor()) {
                it.setValue(policyFactory.sanitize(it.getValue()));
            }
            customMapper.insert(it);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Article bean, ArticleExt ext, Integer userId, Integer orgId,
                       List<String> tagNames, Map<String, Object> customs) {
        bean.setUserId(userId);
        bean.setOrgId(orgId);
        ext.setCreated(OffsetDateTime.now());
        bean.setId(seqService.getNextVal(Article.TABLE_NAME));
        bean.setWithImage(StringUtils.isNotBlank(ext.getImage()));
        Channel channel = channelService.select(bean.getChannelId());
        if (channel == null) {
            throw new IllegalArgumentException("Channel not found. bean.channelId: " + bean.getChannelId());
        }
        String processKey = channel.getProcessKey();
        if (bean.getStatus() == Article.STATUS_PUBLISHED && StringUtils.isNotBlank(processKey)) {
            startProcess(bean, processKey, userId);
        }
        ext.setId(bean.getId());
        ext.setText(policyFactory.sanitize(ext.getText()));
        mapper.insert(bean);
        extMapper.insert(ext);
        bufferMapper.insert(new ArticleBuffer(bean.getId()));
        List<ArticleCustom> customList = Article.disassembleCustoms(channel.getArticleModel(), bean.getId(), customs);
        insertRelatedList(bean.getId(), bean.getSiteId(), userId, tagNames, customList);
        attachmentService.insertRefer(Article.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
        Optional.ofNullable(select(bean.getId())).ifPresent(article -> articleLucene.save(EsArticle.of(article)));
    }

    private void startProcess(Article article, String processKey, Integer userId) {
        try {
            Authentication.setAuthenticatedUserId(userId.toString());
            ProcessInstance instance = runtimeService.startProcessInstanceByKeyAndTenantId(
                    processKey, article.getId().toString(),
                    ImmutableMap.of(
                            PROCESS_VARIABLE_CHANNEL_ID, article.getChannelId(),
                            PROCESS_VARIABLE_ORG_ID, article.getOrgId(),
                            PROCESS_VARIABLE_USER_ID, article.getUserId()
                    ),
                    article.getSiteId().toString());
            article.setStatus(Article.STATUS_PENDING);
            article.setProcessInstanceId(instance.getProcessInstanceId());
        } finally {
            Authentication.setAuthenticatedUserId(null);
        }
    }

    private void deleteProcessIfNecessary(Article article, User user) {
        short status = article.getStatus();
        String processInstanceId = article.getProcessInstanceId();
        // 待审核和审核中的，要删除流程
        boolean hasProcessInstanceId = (status == STATUS_PENDING || status == STATUS_REVIEWING)
                && StringUtils.isNotBlank(processInstanceId);
        if (hasProcessInstanceId &&
                runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).count() > 0) {
            runtimeService.deleteProcessInstance(processInstanceId, user.getUsername() + ":cancel");
        }
    }

    /**
     * 提交。草稿、待审核、已归档、已退回、已下线、已删除，可以提交。
     */
    public void submit(Article article, Integer userId) {
        String processKey = article.getChannel().getProcessKey();
        short status = article.getStatus();
        if (status == STATUS_DRAFT || status == STATUS_PENDING || status == STATUS_ARCHIVED
                || status == STATUS_REJECTED || status == STATUS_OFFLINE || status == STATUS_DELETED) {
            if (StringUtils.isNotBlank(processKey)) {
                // 有流程且在待审核状态无需处理，直接返回
                if (status == STATUS_PENDING) {
                    return;
                }
                startProcess(article, processKey, userId);
            } else {
                article.setStatus(STATUS_PUBLISHED);
                article.setProcessInstanceId(null);
            }
            update(article, article.getExt());
        }
    }

    public void archive(Article article) {
        short status = article.getStatus();
        if (status == STATUS_PUBLISHED) {
            article.setStatus(STATUS_ARCHIVED);
            update(article);
        }
    }

    public void offline(Article article, User user) {
        short status = article.getStatus();
        if (status == STATUS_PUBLISHED || status == STATUS_ARCHIVED || status == STATUS_READY
                || status == STATUS_PENDING || status == STATUS_REVIEWING) {
            deleteProcessIfNecessary(article, user);
            article.setStatus(STATUS_OFFLINE);
            update(article);
        }
    }

    public void delete(Article article, User user) {
        deleteProcessIfNecessary(article, user);
        article.setStatus(STATUS_DELETED);
        update(article);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Article bean, ArticleExt ext, Integer userId,
                       List<String> tagNames, Map<String, Object> customs) {
        bean.setModifiedUserId(userId);
        bean.setWithImage(StringUtils.isNotBlank(ext.getImage()));
        ext.setModified(OffsetDateTime.now());
        ext.setText(policyFactory.sanitize(ext.getText()));
        mapper.update(bean);
        extMapper.update(ext);
        bean.setExt(ext);
        Channel channel = Optional.ofNullable(channelService.select(bean.getChannelId()))
                .orElseThrow(() -> new IllegalArgumentException("bean.channelId cannot be null"));
        List<ArticleCustom> customList = Article.disassembleCustoms(channel.getArticleModel(), bean.getId(), customs);
        // 要先将修改后的数据放入bean中，否则bean.getAttachmentUrls()会获取修改前的值
        bean.setCustomList(customList);
        customMapper.deleteByArticleId(bean.getId());
        tagService.reduceReferByArticleId(bean.getId());
        articleTagMapper.deleteByArticleId(bean.getId());
        insertRelatedList(bean.getId(), bean.getSiteId(), userId, tagNames, customList);
        attachmentService.updateRefer(Article.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
        Optional.ofNullable(select(bean.getId())).ifPresent(article -> articleLucene.update(EsArticle.of(article)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Article bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ArticleExt ext) {
        extMapper.update(ext);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Article bean, ArticleExt ext) {
        mapper.update(bean);
        extMapper.update(ext);
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
                update(dest);
            }
        }
        deleteProcessIfNecessary(bean, user);
        attachmentService.deleteRefer(Article.TABLE_NAME, bean.getId());
        extMapper.delete(bean.getId());
        bufferMapper.delete(bean.getId());
        customMapper.deleteByArticleId(bean.getId());
        statMapper.deleteByArticleId(bean.getId());
        tagService.reduceReferByArticleId(bean.getId());
        articleTagMapper.deleteByArticleId(bean.getId());
        int count = mapper.delete(bean.getId());
        articleLucene.deleteById(bean.getId());
        htmlService.deleteArticleHtml(bean);
        return count;
    }

    @Nullable
    public Article select(Integer id) {
        return mapper.select(id);
    }

    public List<Article> selectList(ArticleArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), Article.TABLE_NAME, "id_desc");
        List<QueryInfo.WhereCondition> customsCondition = CustomFieldQuery.parse(args.getCustomsQueryMap());
        return mapper.selectAll(queryInfo, customsCondition, args.getSubChannelId(), args.getSubOrgId());
    }

    public List<Article> selectList(ArticleArgs args, int offset, int limit) {
        return PageHelper.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Article> selectPage(ArticleArgs args, int page, int pageSize) {
        return PageHelper.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    public List<Article> listByIds(Collection<Integer> ids) {
        return mapper.listByIds(ids);
    }

    public List<Article> selectByMinId(Integer minId, @Nullable Integer siteId, @Nullable Integer subChannelId,
                                       int offset, int limit) {
        ArticleArgs args = ArticleArgs.of().siteId(siteId).subChannelId(subChannelId).minId(minId)
                .status(Arrays.asList(STATUS_PUBLISHED, STATUS_ARCHIVED)).orderById();
        return selectList(args, offset, limit);
    }

    public List<Article> listBySiteIdForSiteMap(Integer siteId, @Nullable Integer minId, int limit) {
        return PageHelper.offsetPage(0, limit, false).doSelectPage(() -> mapper.listBySiteIdForSitemap(siteId, minId));
    }

    @Nullable
    public Article findNext(Integer id, OffsetDateTime publishDate, Integer channelId) {
        List<Article> list = PageHelper.offsetPage(0, 1, false).doSelectPage(() ->
                mapper.findNext(id, publishDate, channelId));
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    @Nullable
    public Article findPrev(Integer id, OffsetDateTime publishDate, Integer channelId) {
        List<Article> list = PageHelper.offsetPage(0, 1, false).doSelectPage(() ->
                mapper.findPrev(id, publishDate, channelId));
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public int countBySiteId(Integer siteId) {
        return mapper.countBySiteId(siteId, Collections.emptyList());
    }

    public Map<String, Object> statForSitemap(Integer siteId, int limit) {
        List<Map<String, Object>> list = PageHelper.offsetPage(0, limit, false).doSelectPage(() ->
                mapper.statForSitemap(siteId, Collections.emptyList()));
        if (list.isEmpty()) {
            return Collections.emptyMap();
        }
        return list.get(0);
    }

    public boolean existsByChannelId(Integer channelId) {
        return PageHelper.offsetPage(0, 1, false).<Number>doSelectPage(() ->
                mapper.countByChannelId(channelId)).iterator().next().intValue() > 0;
    }

    public boolean existsByUserId(Integer userId) {
        return PageHelper.offsetPage(0, 1, false).<Number>doSelectPage(() ->
                mapper.countByUserId(userId)).iterator().next().intValue() > 0;
    }

    @Override
    public void preUserDelete(Integer userId) {
        if (existsByUserId(userId)) {
            throw new LogicException("error.refer.article");
        }
    }

    @Override
    public void preChannelDelete(Integer channelId) {
        if (existsByChannelId(channelId)) {
            throw new LogicException("error.refer.article");
        }
    }

    @Override
    public void preSiteDelete(Integer siteId) {
        extMapper.deleteBySiteId(siteId);
        bufferMapper.deleteBySiteId(siteId);
        statMapper.deleteBySiteId(siteId);
        articleTagMapper.deleteBySiteId(siteId);
        customMapper.deleteBySiteId(siteId);
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        // 栏目(100), 区块(200), **文章(300)**、区块项(400)
        return 300;
    }
}