package com.ujcms.cms.ext.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.FrontendController;
import com.ujcms.cms.ext.domain.MessageBoard;
import com.ujcms.cms.ext.service.MessageBoardService;
import com.ujcms.common.web.exception.Http401Exception;
import com.ujcms.common.web.exception.Http403Exception;
import com.ujcms.common.web.exception.Http404Exception;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 留言板 Controller
 *
 * @author PONY
 */
@FrontendController("frontendMessageBoardController")
public class MessageBoardController {
    private final MessageBoardService service;

    public MessageBoardController(MessageBoardService service) {
        this.service = service;
    }

    private static final String TEMPLATE = "sys_message_board";
    private static final String TEMPLATE_FORM = "sys_message_board_form";
    private static final String TEMPLATE_ITEM = "sys_message_board_item";

    @GetMapping("/message-board")
    public String index(Site site) {
        validateEnabled(site);
        return site.assembleTemplate(TEMPLATE);
    }

    @GetMapping("/message-board/create")
    public String create(Site site) {
        User user = Contexts.findCurrentUser();
        validateEnabled(site);
        validateLoginRequired(site, user);
        return site.assembleTemplate(TEMPLATE_FORM);
    }

    @GetMapping("/message-board/{id}")
    public String show(Site site, @PathVariable Long id, Map<String, Object> modelMap) {
        validateEnabled(site);
        MessageBoard messageBoard = service.select(id);
        // 前台只允许查看已审核且公开的留言
        if (messageBoard == null || !messageBoard.isDisplayable()) {
            throw new Http404Exception(MessageBoard.NOT_FOUND + id);
        }
        modelMap.put("messageBoard", messageBoard);
        return site.assembleTemplate(TEMPLATE_ITEM);
    }

    private void validateEnabled(Site site) {
        if (!site.getMessageBoard().isEnabled()) {
            throw new Http403Exception("MessageBoard is not enabled.");
        }
    }

    private void validateLoginRequired(Site site, User currentUser) {
        if (site.getMessageBoard().isLoginRequired() && currentUser == null) {
            throw new Http401Exception("MessageBoard login required.");
        }
    }

}
