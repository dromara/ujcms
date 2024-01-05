package com.ujcms.cms.ext.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.service.SeqService;
import com.ujcms.cms.ext.domain.MessageBoardType;
import com.ujcms.cms.ext.domain.base.MessageBoardTypeBase;
import com.ujcms.cms.ext.mapper.MessageBoardTypeMapper;
import com.ujcms.cms.ext.service.args.MessageBoardTypeArgs;
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

    private final SeqService seqService;

    public MessageBoardTypeService(MessageBoardTypeMapper mapper, SeqService seqService) {
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(MessageBoardType bean) {
        bean.setId(seqService.getNextVal(MessageBoardTypeBase.TABLE_NAME));
        bean.setOrder(System.currentTimeMillis());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(MessageBoardType bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void moveOrder(Integer fromId, Integer toId) {
        OrderEntityUtils.move(mapper, fromId, toId);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public MessageBoardType select(Integer id) {
        return mapper.select(id);
    }

    public List<MessageBoardType> selectList(MessageBoardTypeArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), MessageBoardTypeBase.TABLE_NAME, "order");
        return mapper.selectAll(queryInfo);
    }

    public List<MessageBoardType> selectList(MessageBoardTypeArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }
}