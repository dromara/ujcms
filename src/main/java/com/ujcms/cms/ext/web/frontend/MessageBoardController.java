package com.ujcms.cms.ext.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.cms.ext.domain.MessageBoard;
import com.ujcms.cms.ext.service.MessageBoardService;
import com.ujcms.commons.web.exception.Http401Exception;
import com.ujcms.commons.web.exception.Http403Exception;
import com.ujcms.commons.web.exception.Http404Exception;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 留言板 Controller
 *
 * @author PONY
 */
@Controller("frontendMessageBoardController")
public class MessageBoardController {
    private final SiteResolver siteResolver;
    private final MessageBoardService service;

    public MessageBoardController(SiteResolver siteResolver, MessageBoardService service) {
        this.siteResolver = siteResolver;
        this.service = service;
    }

    private static final String TEMPLATE = "sys_message_board";
    private static final String TEMPLATE_FORM = "sys_message_board_form";
    private static final String TEMPLATE_ITEM = "sys_message_board_item";

    @GetMapping({"/message-board", "/{subDir:[\\w-]+}/message-board"})
    public String index(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        validateEnabled(site);
        return site.assembleTemplate(TEMPLATE);
    }

    @GetMapping({"/message-board/create", "/{subDir:[\\w-]+}/message-board/create"})
    public String create(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        User user = Contexts.findCurrentUser();
        validateEnabled(site);
        validateLoginRequired(site, user);
        return site.assembleTemplate(TEMPLATE_FORM);
    }

    @GetMapping({"/message-board/{id}", "/{subDir:[\\w-]+}/message-board/{id}"})
    public String show(@PathVariable(required = false) String subDir, @PathVariable Long id,
                       HttpServletRequest request, Map<String, Object> modelMap) {
        Site site = siteResolver.resolve(request, subDir);
        validateEnabled(site);
        MessageBoard messageBoard = service.select(id);
        if (messageBoard == null) {
            throw new Http404Exception("MessageBoard not found. ID=" + id);
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
