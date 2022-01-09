package com.ujcms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ofwise.util.query.CustomFieldQuery;
import com.ofwise.util.query.QueryInfo;
import com.ofwise.util.query.QueryParser;
import com.ujcms.core.domain.Article;
import com.ujcms.core.domain.ArticleBuffer;
import com.ujcms.core.domain.ArticleCustom;
import com.ujcms.core.domain.ArticleExt;
import com.ujcms.core.domain.ArticleFile;
import com.ujcms.core.domain.ArticleImage;
import com.ujcms.core.exception.LogicException;
import com.ujcms.core.listener.ChannelDeleteListener;
import com.ujcms.core.listener.UserDeleteListener;
import com.ujcms.core.lucene.ArticleLucene;
import com.ujcms.core.lucene.domain.EsArticle;
import com.ujcms.core.mapper.ArticleBufferMapper;
import com.ujcms.core.mapper.ArticleCustomMapper;
import com.ujcms.core.mapper.ArticleExtMapper;
import com.ujcms.core.mapper.ArticleFileMapper;
import com.ujcms.core.mapper.ArticleImageMapper;
import com.ujcms.core.mapper.ArticleMapper;
import com.ujcms.core.mapper.ArticleStatMapper;
import org.apache.commons.lang3.StringUtils;
import org.owasp.html.PolicyFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 文章 Service
 *
 * @author PONY
 */
@Service
public class ArticleService implements ChannelDeleteListener, UserDeleteListener {
    private ArticleLucene articleLucene;
    private PolicyFactory policyFactory;
    private ArticleMapper mapper;
    private ArticleExtMapper extMapper;
    private ArticleBufferMapper bufferMapper;
    private ArticleCustomMapper customMapper;
    private ArticleImageMapper imageMapper;
    private ArticleFileMapper fileMapper;
    private ArticleStatMapper statMapper;
    private AttachmentService attachmentService;
    private SeqService seqService;

    public ArticleService(ArticleLucene articleLucene, PolicyFactory policyFactory, ArticleMapper mapper,
                          ArticleExtMapper extMapper, ArticleBufferMapper bufferMapper,
                          ArticleCustomMapper customMapper, ArticleImageMapper imageMapper,
                          ArticleFileMapper fileMapper, ArticleStatMapper statMapper,
                          AttachmentService attachmentService, SeqService seqService) {
        this.articleLucene = articleLucene;
        this.policyFactory = policyFactory;
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

    private void insertRelatedList(int articleId, List<ArticleCustom> customList, List<ArticleImage> imageList, List<ArticleFile> fileList) {
        customList.forEach(it -> {
            it.setArticleId(articleId);
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
    public int delete(int id) {
        Article bean = select(id);
        if (bean == null) {
            return 0;
        }
        attachmentService.deleteRefer(Article.TABLE_NAME, id);
        extMapper.delete(id);
        bufferMapper.delete(id);
        customMapper.deleteByArticleId(id);
        statMapper.deleteByArticleId(id);
        int count = mapper.delete(id);
        articleLucene.deleteById(id);
        return count;
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    public void reindex() {
        reindex(null);
    }

    public void reindex(@Nullable Integer siteId) {
        if (siteId != null) {
            articleLucene.deleteBySiteId(siteId);
        } else {
            articleLucene.deleteAll();
        }
        Map<String, Object> queryMap = new HashMap<>(16);
        int minId = 0;
        int offset = 0, limit = 100, size = limit;
        if (siteId != null) {
            queryMap.put("EQ_siteId", siteId);
        }
        queryMap.put("OrderBy", "id");
        while (size >= limit) {
            queryMap.put("GT_id", minId);
            List<Article> list = selectList(queryMap, null, null, offset, limit);
            size = list.size();
            for (int i = 0; i < size; i++) {
                Article article = list.get(i);
                articleLucene.save(EsArticle.of(article));
                if (i == size - 1) {
                    minId = article.getId();
                }
            }
        }
    }

    @Nullable
    public Article select(int id) {
        return mapper.select(id);
    }

    public List<Article> selectList(@Nullable Map<String, Object> queryMap,
                                    @Nullable Map<String, String> customsQueryMap,
                                    @Nullable Integer channelId) {
        QueryInfo queryInfo = QueryParser.parse(queryMap, Article.TABLE_NAME, "id_desc");
        List<QueryInfo.WhereCondition> customsCondition = CustomFieldQuery.parse(customsQueryMap);
        return mapper.selectAll(queryInfo, customsCondition, channelId);
    }

    public List<Article> selectList(@Nullable Map<String, Object> queryMap,
                                    @Nullable Map<String, String> customsQueryMap,
                                    @Nullable Integer channelId,
                                    @Nullable Integer offset, @Nullable Integer limit) {
        return PageHelper.offsetPage(offset == null ? 0 : offset, limit == null ? Integer.MAX_VALUE : limit, false)
                .doSelectPage(() -> selectList(queryMap, customsQueryMap, channelId));
    }

    public Page<Article> selectPage(@Nullable Map<String, Object> queryMap,
                                    @Nullable Map<String, String> customsQueryMap,
                                    @Nullable Integer channelId,
                                    int page, int pageSize) {
        return PageHelper.startPage(page, pageSize)
                .doSelectPage(() -> selectList(queryMap, customsQueryMap, channelId));
    }

    @Nullable
    public Article findNext(int id, OffsetDateTime publishDate, int channelId) {
        List<Article> list = PageHelper.offsetPage(0, 1, false)
                .doSelectPage(() -> mapper.findNext(id, publishDate, channelId));
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    @Nullable
    public Article findPrev(int id, OffsetDateTime publishDate, int channelId) {
        List<Article> list = PageHelper.offsetPage(0, 1, false)
                .doSelectPage(() -> mapper.findPrev(id, publishDate, channelId));
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }


    @Override
    public void preChannelDelete(int channelId) {
        if (mapper.countByChannelId(channelId) > 0) {
            throw new LogicException("error.refer.article");
        }
    }

    @Override
    public void preUserDelete(int userId) {
        if (mapper.countByUserId(userId) > 0) {
            throw new LogicException("error.refer.article");
        }
    }
}