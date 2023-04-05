package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ujcms.cms.core.domain.Tag;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.ArticleTagMapper;
import com.ujcms.cms.core.mapper.TagMapper;
import com.ujcms.cms.core.service.args.TagArgs;
import com.ujcms.util.query.QueryInfo;
import com.ujcms.util.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class TagService implements SiteDeleteListener {
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper mapper;
    private final SeqService seqService;

    public TagService(ArticleTagMapper articleTagMapper, TagMapper mapper, SeqService seqService) {
        this.articleTagMapper = articleTagMapper;
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Tag bean) {
        bean.setId(seqService.getNextVal(Tag.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Tag bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        articleTagMapper.deleteByTagId(id);
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Tag select(Integer id) {
        return mapper.select(id);
    }

    public List<Tag> selectList(TagArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), Tag.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<Tag> selectList(TagArgs args, int offset, int limit) {
        return PageHelper.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Tag> selectPage(TagArgs args, int page, int pageSize) {
        return PageHelper.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    @Nullable
    public Tag selectByName(Integer siteId, String name) {
        return mapper.selectByName(siteId, name);
    }

    public int reduceReferByArticleId(Integer articleId) {
        return mapper.reduceReferByArticleId(articleId);
    }

    @Override
    public void preSiteDelete(Integer siteId) {
        mapper.delete(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }

}