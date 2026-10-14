package com.ujcms.cms.ext.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.ext.domain.MessageBoardType;
import com.ujcms.cms.ext.domain.generated.GeneratedMessageBoardType;
import com.ujcms.cms.ext.mapper.MessageBoardMapper;
import com.ujcms.cms.ext.mapper.MessageBoardTypeMapper;
import com.ujcms.cms.ext.service.args.MessageBoardTypeArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.db.order.OrderEntityUtils;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.web.exception.LogicException;
import org.jspecify.annotations.Nullable;
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
public class MessageBoardTypeService implements SiteDeleteListener {
    private final MessageBoardMapper messageBoardMapper;
    private final MessageBoardTypeMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public MessageBoardTypeService(MessageBoardMapper messageBoardMapper, MessageBoardTypeMapper mapper,
                                   SnowflakeSequence snowflakeSequence) {
        this.messageBoardMapper = messageBoardMapper;
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

    /**
     * 删除留言类型。留言必须有类型（type_id_ 非空），且是访客提交的数据、不随类型级联删除：类型下还有留言即拒绝。
     * 不先判断的话，删除会撞上外键约束，用户看到的是一条原始的数据库错误
     */
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        if (messageBoardMapper.existsByTypeId(id) > 0) {
            MessageBoardType bean = mapper.select(id);
            throw new LogicException("error.messageBoardType.inUse", bean != null ? bean.getName() : String.valueOf(id));
        }
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
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedMessageBoardType.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<MessageBoardType> selectList(MessageBoardTypeArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    @Override
    public void preSiteDelete(Long siteId) {
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }
}