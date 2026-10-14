package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.component.WordScreeningService;
import com.ujcms.cms.core.component.WordScreeningService.WordHit;
import com.ujcms.cms.core.component.WordScreeningService.WordType;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.cms.core.security.PolicyTier;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.ext.component.UgcSubmitDispatcher;
import com.ujcms.cms.ext.domain.MessageBoard;
import com.ujcms.cms.ext.domain.generated.GeneratedMessageBoard;
import com.ujcms.cms.ext.listener.UgcSubmitListener.UgcSubmission;
import com.ujcms.cms.ext.mapper.MessageBoardMapper;
import com.ujcms.cms.ext.service.args.MessageBoardArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
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
public class MessageBoardService implements SiteDeleteListener {
    private final AttachmentService attachmentService;
    private final MessageBoardMapper mapper;
    private final SnowflakeSequence snowflakeSequence;
    private final HtmlSanitizeService sanitizeService;
    private final WordScreeningService wordScreeningService;
    private final UgcSubmitDispatcher ugcSubmitDispatcher;

    public MessageBoardService(AttachmentService attachmentService, MessageBoardMapper mapper,
                               SnowflakeSequence snowflakeSequence, HtmlSanitizeService sanitizeService,
                               WordScreeningService wordScreeningService, UgcSubmitDispatcher ugcSubmitDispatcher) {
        this.attachmentService = attachmentService;
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
        this.sanitizeService = sanitizeService;
        this.wordScreeningService = wordScreeningService;
        this.ugcSubmitDispatcher = ugcSubmitDispatcher;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(MessageBoard bean, Long userId, String ip) {
        bean.setId(snowflakeSequence.nextId());
        bean.setUserId(userId);
        bean.setIp(ip);
        // text 为纯文本（前台/后台均为 textarea），由模板在输出时转义（bbcode()/FreeMarker escape），
        // 不做 HTML 清理：OWASP 输出会把全角标点编码成 &#xff1f; 等实体，再经模板转义即成乱码。
        // replyText 为富文本 HTML，模板按原样输出，须在 update 时按 STRICT 清理。
        mapper.insert(bean);
        attachmentService.insertRefer(GeneratedMessageBoard.TABLE_NAME,bean.getId(), bean.getAttachmentUrls());
        // 留言默认即待审核，词库命中无须改状态（docs/spec/ai/screening.md §4.2）；命中随事件带出供审核流水线记录
        String text = StringUtils.defaultString(bean.getTitle())
                + "\n" + StringUtils.defaultString(bean.getText());
        List<WordHit> dictHits = wordScreeningService.screen(text).stream()
                .filter(hit -> hit.type() == WordType.SENSITIVE).toList();
        ugcSubmitDispatcher.dispatch(new UgcSubmission(UgcSubmission.TYPE_MESSAGE_BOARD, bean.getId(),
                bean.getSiteId(), userId, text, dictHits, bean.getStatus()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(MessageBoard bean) {
        bean.setReplyText(sanitizeService.sanitize(bean.getReplyText(), PolicyTier.STRICT));
        bean.setReplied(StringUtils.isNotBlank(bean.getReplyText()));
        mapper.update(bean);
        attachmentService.updateRefer(GeneratedMessageBoard.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
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
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedMessageBoard.TABLE_NAME, "id_desc");
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

    @Override
    public void preSiteDelete(Long siteId) {
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        // 留言外键引用留言类型，须先于留言类型(100)删除
        return 200;
    }
}