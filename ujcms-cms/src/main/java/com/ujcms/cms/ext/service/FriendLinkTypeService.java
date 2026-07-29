package com.ujcms.cms.ext.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.ext.domain.FriendLinkType;
import com.ujcms.cms.ext.domain.generated.GeneratedFriendLinkType;
import com.ujcms.cms.ext.mapper.FriendLinkTypeMapper;
import com.ujcms.cms.ext.service.args.FriendLinkTypeArgs;
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
 * 友情链接类型 Service
 *
 * @author PONY
 */
@Service
public class FriendLinkTypeService {
    private final FriendLinkTypeMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public FriendLinkTypeService(FriendLinkTypeMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(FriendLinkType bean) {
        bean.setId(snowflakeSequence.nextId());
        bean.setOrder(bean.getId());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(FriendLinkType bean) {
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
    public FriendLinkType select(Long id) {
        return mapper.select(id);
    }

    @Nullable
    public FriendLinkType selectByAlias(Long siteId, String alias) {
        return mapper.listByAlias(siteId, alias).stream().findFirst().orElse(null);
    }

    public boolean existsByAlias(String alias, Long siteId) {
        return mapper.existsByAlias(alias, siteId) > 0;
    }

    public List<FriendLinkType> selectList(FriendLinkTypeArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedFriendLinkType.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<FriendLinkType> selectList(FriendLinkTypeArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }
}
