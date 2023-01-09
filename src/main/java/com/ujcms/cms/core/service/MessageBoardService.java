package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ujcms.cms.core.domain.MessageBoard;
import com.ujcms.cms.core.mapper.MessageBoardMapper;
import com.ujcms.cms.core.service.args.MessageBoardArgs;
import com.ujcms.util.query.QueryInfo;
import com.ujcms.util.query.QueryParser;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * @author PONY
 */
@Service
public class MessageBoardService {
    private final AttachmentService attachmentService;
    private final MessageBoardMapper mapper;
    private final SeqService seqService;

    public MessageBoardService(AttachmentService attachmentService, MessageBoardMapper mapper, SeqService seqService) {
        this.attachmentService = attachmentService;
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(MessageBoard bean, Integer userId, String ip) {
        bean.setId(seqService.getNextVal(MessageBoard.TABLE_NAME));
        bean.setUserId(userId);
        bean.setIp(ip);
        mapper.insert(bean);
        attachmentService.insertRefer(MessageBoard.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(MessageBoard bean) {
        bean.setReplied(StringUtils.isNotBlank(bean.getReplyText()));
        mapper.update(bean);
        attachmentService.updateRefer(MessageBoard.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(int id) {
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public MessageBoard select(int id) {
        return mapper.select(id);
    }

    public List<MessageBoard> selectList(MessageBoardArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), MessageBoard.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<MessageBoard> selectList(MessageBoardArgs args, int offset, int limit) {
        return PageHelper.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<MessageBoard> selectPage(MessageBoardArgs args, int page, int pageSize) {
        return PageHelper.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }
}