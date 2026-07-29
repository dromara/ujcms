package com.ujcms.cms.ext.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.ext.domain.FriendLink;
import com.ujcms.cms.ext.domain.generated.GeneratedFriendLink;
import com.ujcms.cms.ext.mapper.FriendLinkMapper;
import com.ujcms.cms.ext.service.args.FriendLinkArgs;
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
 * 友情链接 Service
 *
 * @author PONY
 */
@Service
public class FriendLinkService {
    private final FriendLinkMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public FriendLinkService(FriendLinkMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(FriendLink bean) {
        bean.setId(snowflakeSequence.nextId());
        bean.setOrder(bean.getId());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(FriendLink bean) {
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
    public FriendLink select(Long id) {
        return mapper.select(id);
    }

    public List<FriendLink> selectList(FriendLinkArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedFriendLink.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<FriendLink> selectList(FriendLinkArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }
}
