package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.google.common.collect.ImmutableMap;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleBuffer;
import com.ujcms.cms.core.domain.ArticleCustom;
import com.ujcms.cms.core.domain.ArticleExt;
import com.ujcms.cms.core.domain.ArticleFile;
import com.ujcms.cms.core.domain.ArticleImage;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.generator.HtmlService;
import com.ujcms.cms.core.listener.ChannelDeleteListener;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.lucene.domain.EsArticle;
import com.ujcms.cms.core.mapper.ArticleBufferMapper;
import com.ujcms.cms.core.mapper.ArticleCustomMapper;
import com.ujcms.cms.core.mapper.ArticleExtMapper;
import com.ujcms.cms.core.mapper.ArticleFileMapper;
import com.ujcms.cms.core.mapper.ArticleImageMapper;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.ArticleStatMapper;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.ujcms.cms.core.domain.Article.*;

/**
 * 文章 Service
 *
 * @author PONY
 */
@Service
public class ArticleService implements ChannelDeleteListener, UserDeleteListener, SiteDeleteListener {
    private HtmlService htmlService;
    private PolicyFactory policyFactory;
    private RuntimeService runtimeService;
    private ChannelService channelService;
    private ArticleLucene articleLucene;
    private ArticleMapper mapper;
    private ArticleExtMapper extMapper;
    private ArticleBufferMapper bufferMapper;
    private ArticleCustomMapper customMapper;
    private ArticleImageMapper imageMapper;
    private ArticleFileMapper fileMapper;
    private ArticleStatMapper statMapper;
    private AttachmentService attachmentService;
    private SeqService seqService;

    public ArticleService(HtmlService htmlService, PolicyFactory policyFactory,
                          RuntimeService runtimeService, ChannelService channelService,
                          ArticleLucene articleLucene,
                          ArticleMapper mapper, ArticleExtMapper extMapper, ArticleBufferMapper bufferMapper,
                          ArticleCustomMapper customMapper, ArticleImageMapper imageMapper,
                          ArticleFileMapper fileMapper, ArticleStatMapper statMapper,
                          AttachmentService attachmentService, SeqService seqService) {
        this.htmlService = htmlService;
        this.policyFactory = policyFactory;
        this.runtimeService = runtimeService;
        this.channelService = channelService;
        this.articleLucene = articleLucene;
        this.mapper = mapper;
        this.extMapper = extMapper;
        this.bufferMapper = bufferMapper;
        this.customMapper = customMapper;
        this.imageMapper = imageMapper;
        this.fileMapper = fileMapper;
        this.statMapper = statMapper;
        this.attachmentService = attachmentService;
        this.seqService = seqService;
    }

    private void insertRelatedList(Integer articleId, List<ArticleCustom> customList,
                                   List<ArticleImage> imageList, List<ArticleFile> fileList) {
        customList.forEach(it -> {
            it.setId(seqService.getNextValLong(ArticleCustom.TABLE_NAME));
            it.setArticleId(articleId);
            if (it.isRichEditor()) {
                it.setValue(policyFactory.sanitize(it.getValue()));
            }
            customMapper.insert(it);
        });
        imageList.forEach(it -> {
            it.setId(seqService.getNextValLong(ArticleImage.TABLE_NAME));
            it.setArticleId(articleId);
            imageMapper.insert(it);
        });
        fileList.forEach(it -> {
            it.setId(seqService.getNextValLong(ArticleFile.TABLE_NAME));
            it.setArticleId(articleId);
            fileMapper.insert(it);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Article bean, ArticleExt ext, Integer userId, Integer orgId,
                       Map<String, Object> custaoms, List<ArticleImage> imageList, List<ArticleFile> fileList) {
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
        List<ArticleCustom> customList = Article.disassembleCustoms(channel.getArticleModel(), bean.getId(), custaoms);
        insertRelatedList(bean.getId(), customList, imageList, fileList);
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

    private void deleteProcessIfNecessary(Article article) {
        short status = article.getStatus();
        String processInstanceId = article.getProcessInstanceId();
        // 待审核和审核中的，要删除流程
        boolean hasProcessInstanceId = (status == STATUS_PENDING || status == STATUS_REVIEWING)
                && StringUtils.isNotBlank(processInstanceId);
        if (hasProcessInstanceId) {
            runtimeService.deleteProcessInstance(processInstanceId, "cancel");
        }
    }

    /**
     * 提交。草稿、待审核、已归档、已退回、已下线、已删除，可以提交。
     */
    public void submit(Article article, Integer userId) {
        String processKey = article.getChannel().getProcessKey();
        short status = article.getStatus();
        if (status == STATUS_DRAFT || status == STATUS_REJECTED || status == STATUS_OFFLINE || status == STATUS_DELETED
                || status == STATUS_PENDING) {
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

    public void offline(Article article) {
        short status = article.getStatus();
        if (status == STATUS_PUBLISHED || status == STATUS_ARCHIVED || status == STATUS_READY
                || status == STATUS_PENDING || status == STATUS_REVIEWING) {
            deleteProcessIfNecessary(article);
            article.setStatus(STATUS_OFFLINE);
            update(article);
        }
    }

    public void delete(Article article) {
        deleteProcessIfNecessary(article);
        article.setStatus(STATUS_DELETED);
        update(article);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Article bean, ArticleExt ext, Integer userId,
                       Map<String, Object> customs, List<ArticleImage> imageList, List<ArticleFile> fileList) {
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
        bean.setImageList(imageList);
        bean.setFileList(fileList);
        customMapper.deleteByArticleId(bean.getId());
        imageMapper.deleteByArticleId(bean.getId());
        fileMapper.deleteByArticleId(bean.getId());
        insertRelatedList(bean.getId(), customList, imageList, fileList);
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
    public int completelyDelete(Integer id) {
        Article bean = select(id);
        if (bean == null) {
            return 0;
        }
        deleteProcessIfNecessary(bean);
        attachmentService.deleteRefer(Article.TABLE_NAME, id);
        extMapper.delete(id);
        bufferMapper.delete(id);
        customMapper.deleteByArticleId(id);
        imageMapper.deleteByArticleId(id);
        fileMapper.deleteByArticleId(id);
        statMapper.deleteByArticleId(id);
        int count = mapper.delete(id);
        articleLucene.deleteById(id);
        htmlService.deleteArticleHtml(bean);
        return count;
    }

    @Transactional(rollbackFor = Exception.class)
    public int completelyDelete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::completelyDelete).sum();
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
        return PageHelper.offsetPage(0, 1, false).doCount(() ->
                mapper.countByChannelId(channelId)) > 0;
    }

    public boolean existsByUserId(Integer userId) {
        return PageHelper.offsetPage(0, 1, false).doCount(() -> mapper.countByUserId(userId)) > 0;
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
        imageMapper.deleteBySiteId(siteId);
        fileMapper.deleteBySiteId(siteId);
        statMapper.deleteBySiteId(siteId);
        customMapper.deleteBySiteId(siteId);
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        // 栏目(100), 区块(200), **文章(300)**、区块项(400)
        return 300;
    }
}