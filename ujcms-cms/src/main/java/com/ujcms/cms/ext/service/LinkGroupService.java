package com.ujcms.cms.ext.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.ext.domain.LinkGroup;
import com.ujcms.cms.ext.domain.generated.GeneratedLinkGroup;
import com.ujcms.cms.ext.mapper.LinkGroupMapper;
import com.ujcms.cms.ext.mapper.LinkMapper;
import com.ujcms.cms.ext.service.args.LinkGroupArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.db.order.OrderEntityUtils;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 链接组 Service
 *
 * @author PONY
 */
@Service
public class LinkGroupService implements SiteDeleteListener {
    private final LinkMapper linkMapper;
    private final LinkGroupMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public LinkGroupService(LinkMapper linkMapper, LinkGroupMapper mapper,
                            SnowflakeSequence snowflakeSequence) {
        this.linkMapper = linkMapper;
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(LinkGroup bean) {
        bean.setId(snowflakeSequence.nextId());
        bean.setOrder(bean.getId());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(LinkGroup bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void moveOrder(Long fromId, Long toId) {
        OrderEntityUtils.move(mapper, fromId, toId);
    }

    /**
     * 删除链接组及其全部链接（先删链接行，避免 RESTRICT 外键裸报错）
     */
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        linkMapper.deleteByGroupId(id);
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public LinkGroup select(Long id) {
        return mapper.select(id);
    }

    @Nullable
    public LinkGroup selectByAlias(Long siteId, String alias) {
        return mapper.listByAlias(siteId, alias).stream().findFirst().orElse(null);
    }

    public boolean existsByAlias(String alias, Long siteId) {
        return mapper.existsByAlias(alias, siteId) > 0;
    }

    public List<LinkGroup> selectList(LinkGroupArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedLinkGroup.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo, args.isQueryLinkCount());
    }

    public List<LinkGroup> selectList(LinkGroupArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    @Override
    public int deleteListenerOrder() {
        // 链接外键引用文章(300)和栏目(100)（channel_id_/article_id_ RESTRICT），须先于两者删除
        return 500;
    }

    @Override
    public void preSiteDelete(Long siteId) {
        linkMapper.deleteBySiteId(siteId);
        mapper.deleteBySiteId(siteId);
    }
}
