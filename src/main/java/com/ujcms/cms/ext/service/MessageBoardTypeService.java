package com.ujcms.cms.ext.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.ext.domain.MessageBoardType;
import com.ujcms.cms.ext.domain.base.MessageBoardTypeBase;
import com.ujcms.cms.ext.mapper.MessageBoardTypeMapper;
import com.ujcms.cms.ext.service.args.MessageBoardTypeArgs;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.db.order.OrderEntityUtils;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 留言类型 Service
 *
 * @author PONY
 */
@Service
public class MessageBoardTypeService {
    private final MessageBoardTypeMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public MessageBoardTypeService(MessageBoardTypeMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(MessageBoardType bean) {
        bean.setId(snowflakeSequence.nextId());
        bean.setOrder(bean.getId());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(MessageBoardType bean) {
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
    public MessageBoardType select(Long id) {
        return mapper.select(id);
    }

    public List<MessageBoardType> selectList(MessageBoardTypeArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), MessageBoardTypeBase.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<MessageBoardType> selectList(MessageBoardTypeArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }
}