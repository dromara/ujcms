package com.ujcms.cms.ext.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.listener.ArticleDeleteListener;
import com.ujcms.cms.core.listener.ChannelDeleteListener;
import com.ujcms.cms.ext.domain.Link;
import com.ujcms.cms.ext.domain.generated.GeneratedLink;
import com.ujcms.cms.ext.mapper.LinkMapper;
import com.ujcms.cms.ext.service.args.LinkArgs;
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
 * 链接 Service。栏目/文章删除监听：先删引用行（link-groups D3——内容删除不因链接受阻，
 * channel_id_/article_id_ 的 RESTRICT 外键仅作监听器缺位时的兜底）。
 *
 * @author PONY
 */
@Service
public class LinkService implements ChannelDeleteListener, ArticleDeleteListener {
    private final LinkMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public LinkService(LinkMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Link bean) {
        bean.setId(snowflakeSequence.nextId());
        bean.setOrder(bean.getId());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Link bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void moveOrder(Long fromId, Long toId) {
        OrderEntityUtils.move(mapper, fromId, toId);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Link select(Long id) {
        return mapper.select(id);
    }

    public List<Link> selectList(LinkArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedLink.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<Link> selectList(LinkArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    /**
     * 仅站点级联删除排序用（栏目/文章删除路径不排序，直接逐监听器回调）
     */
    @Override
    public int deleteListenerOrder() {
        return 500;
    }

    @Override
    public void preChannelDelete(Long channelId) {
        mapper.deleteByChannelId(channelId);
    }

    @Override
    public void preArticleDelete(Long articleId) {
        mapper.deleteByArticleId(articleId);
    }
}
