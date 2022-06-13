package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleBuffer;
import com.ujcms.cms.core.domain.ArticleCustom;
import com.ujcms.cms.core.domain.ArticleExt;
import com.ujcms.cms.core.domain.ArticleFile;
import com.ujcms.cms.core.domain.ArticleImage;
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
import org.owasp.html.PolicyFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 文章 Service
 *
 * @author PONY
 */
@Service
public class ArticleService implements ChannelDeleteListener, UserDeleteListener, SiteDeleteListener {
    private HtmlService htmlService;
    private PolicyFactory policyFactory;
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

    public ArticleService(HtmlService htmlService, PolicyFactory policyFactory, ArticleLucene articleLucene,
                          ArticleMapper mapper, ArticleExtMapper extMapper, ArticleBufferMapper bufferMapper,
                          ArticleCustomMapper customMapper, ArticleImageMapper imageMapper,
                          ArticleFileMapper fileMapper, ArticleStatMapper statMapper,
                          AttachmentService attachmentService, SeqService seqService) {
        this.htmlService = htmlService;
        this.policyFactory = policyFactory;
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
            it.setArticleId(articleId);
            if (it.isRichEditor()) {
                it.setValue(policyFactory.sanitize(it.getValue()));
            }
            customMapper.insert(it);
        });
        imageList.forEach(it -> {
            it.setArticleId(articleId);
            imageMapper.insert(it);
        });
        fileList.forEach(it -> {
            it.setArticleId(articleId);
            fileMapper.insert(it);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Article bean, ArticleExt ext, Integer userId, Integer orgId,
                       List<ArticleCustom> customList, List<ArticleImage> imageList, List<ArticleFile> fileList) {
        bean.setUserId(userId);
        bean.setOrgId(orgId);
        ext.setCreated(OffsetDateTime.now());
        bean.setId(seqService.getNextVal(Article.TABLE_NAME));
        bean.setWithImage(StringUtils.isNotBlank(ext.getImage()));
        ext.setId(bean.getId());
        ext.setText(policyFactory.sanitize(ext.getText()));
        mapper.insert(bean);
        extMapper.insert(ext);
        bufferMapper.insert(new ArticleBuffer(bean.getId()));
        insertRelatedList(bean.getId(), customList, imageList, fileList);
        attachmentService.insertRefer(Article.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
        Optional.ofNullable(select(bean.getId())).ifPresent(article -> articleLucene.save(EsArticle.of(article)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Article bean, ArticleExt ext, Integer userId,
                       List<ArticleCustom> customList, List<ArticleImage> imageList, List<ArticleFile> fileList) {
        bean.setModifiedUserId(userId);
        bean.setWithImage(StringUtils.isNotBlank(ext.getImage()));
        ext.setModified(OffsetDateTime.now());
        ext.setText(policyFactory.sanitize(ext.getText()));
        mapper.update(bean);
        extMapper.update(ext);
        bean.setExt(ext);
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
    public void update(ArticleExt ext) {
        extMapper.update(ext);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        Article bean = select(id);
        if (bean == null) {
            return 0;
        }
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
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Article select(Integer id) {
        return mapper.select(id);
    }

    public List<Article> selectList(ArticleArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), Article.TABLE_NAME, "id_desc");
        List<QueryInfo.WhereCondition> customsCondition = CustomFieldQuery.parse(args.getCustomsQueryMap());
        return mapper.selectAll(queryInfo, customsCondition, args.getSubChannelId());
    }

    public List<Article> selectList(ArticleArgs args, int offset, int limit) {
        return PageHelper.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Article> selectPage(ArticleArgs args, int page, int pageSize) {
        return PageHelper.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    public List<Article> selectByMinId(Integer minId, @Nullable Integer siteId, @Nullable Integer subChannelId,
                                       int offset, int limit) {
        ArticleArgs args = ArticleArgs.of().siteId(siteId).subChannelId(subChannelId).minId(minId).orderById();
        return selectList(args, offset, limit);
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

    public boolean existsByChannelIdAndNotDeletedStatus(Integer channelId) {
        return PageHelper.offsetPage(0, 1, false).doCount(() ->
                mapper.countByChannelIdAndNotDeletedStatus(channelId)) > 0;
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
        if (existsByChannelIdAndNotDeletedStatus(channelId)) {
            throw new LogicException("error.refer.article");
        }
        extMapper.deleteByChannelId(channelId);
        bufferMapper.deleteByChannelId(channelId);
        imageMapper.deleteByChannelId(channelId);
        fileMapper.deleteByChannelId(channelId);
        statMapper.deleteByChannelId(channelId);
        customMapper.deleteByChannelId(channelId);
        mapper.deleteByChannelId(channelId);
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