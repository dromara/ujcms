package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.ext.domain.MessageBoard;
import com.ujcms.cms.ext.domain.base.MessageBoardBase;
import com.ujcms.cms.ext.mapper.MessageBoardMapper;
import com.ujcms.cms.ext.service.args.MessageBoardArgs;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 留言板 Service
 *
 * @author PONY
 */
@Service
public class MessageBoardService {
    private final AttachmentService attachmentService;
    private final MessageBoardMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public MessageBoardService(AttachmentService attachmentService, MessageBoardMapper mapper,
                               SnowflakeSequence snowflakeSequence) {
        this.attachmentService = attachmentService;
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(MessageBoard bean, Long userId, String ip) {
        bean.setId(snowflakeSequence.nextId());
        bean.setUserId(userId);
        bean.setIp(ip);
        mapper.insert(bean);
        attachmentService.insertRefer(MessageBoardBase.TABLE_NAME,bean.getId(), bean.getAttachmentUrls());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(MessageBoard bean) {
        bean.setReplied(StringUtils.isNotBlank(bean.getReplyText()));
        mapper.update(bean);
        attachmentService.updateRefer(MessageBoardBase.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
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
    public MessageBoard select(Long id) {
        return mapper.select(id);
    }

    public List<MessageBoard> selectList(MessageBoardArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), MessageBoardBase.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<MessageBoard> selectList(MessageBoardArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<MessageBoard> selectPage(MessageBoardArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    public long count(MessageBoardArgs args) {
        return PageMethod.count(() -> selectList(args));
    }
}