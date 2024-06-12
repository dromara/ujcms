package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.Tag;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.ArticleTagMapper;
import com.ujcms.cms.core.mapper.TagMapper;
import com.ujcms.cms.core.service.args.TagArgs;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Tag Service
 *
 * @author PONY
 */
@Service
public class TagService implements SiteDeleteListener {
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public TagService(ArticleTagMapper articleTagMapper, TagMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.articleTagMapper = articleTagMapper;
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Tag bean) {
        bean.setId(snowflakeSequence.nextId());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Tag bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        articleTagMapper.deleteByTagId(id);
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Tag select(Long id) {
        return mapper.select(id);
    }

    public List<Tag> selectList(TagArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), Tag.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<Tag> selectList(TagArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Tag> selectPage(TagArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    @Nullable
    public Tag selectByName(Long siteId, String name) {
        return mapper.selectByName(siteId, name);
    }

    public int reduceReferByArticleId(Long articleId) {
        return mapper.reduceReferByArticleId(articleId);
    }

    @Override
    public void preSiteDelete(Long siteId) {
        mapper.delete(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }

}