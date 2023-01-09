package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.MessageBoard;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.MessageBoardService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.util.captcha.CaptchaTokenService;
import com.ujcms.util.web.Entities;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Servlets;
import com.ujcms.util.web.exception.Http400Exception;
import com.ujcms.util.web.exception.Http401Exception;
import com.ujcms.util.web.exception.Http403Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

/**
 * 留言板 Controller
 *
 * @author PONY
 */
@Tag(name = "MessageBoardController", description = "留言板接口")
@RestController
@RequestMapping({API + "/message-board", FRONTEND_API + "/message-board"})
public class MessageBoardController {
    private final CaptchaTokenService captchaTokenService;
    private final MessageBoardService service;
    private final SiteResolver siteResolver;

    public MessageBoardController(CaptchaTokenService captchaTokenService, MessageBoardService service,
                                  SiteResolver siteResolver) {
        this.captchaTokenService = captchaTokenService;
        this.service = service;
        this.siteResolver = siteResolver;
    }

    @Operation(summary = "提交留言")
    @PostMapping
    public ResponseEntity<Responses.Body> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "留言对象。" +
                    "不可用属性：\"userId\", \"replyUserId\", \"replyText\", \"created\", \"replyDate\", \"ip\", \"replied\", \"recommended\", \"status\"")
            @RequestBody @Valid MessageBoardParams params,
            HttpServletRequest request) {
        Site site = siteResolver.resolve(request, params.getSiteId());
        if (!site.getMessageBoard().isEnabled()) {
            throw new Http403Exception("MessageBoard is not enabled.");
        }
        User user = Contexts.findCurrentUser();
        Integer userId;
        if (user == null) {
            if (site.getMessageBoard().isLoginRequired()) {
                throw new Http401Exception("MessageBoard login required.");
            }
            userId = User.ANONYMOUS_ID;
        } else {
            userId = user.getId();
        }
        if (!captchaTokenService.validateCaptcha(params.captchaToken, params.captcha)) {
            throw new Http400Exception("Captcha is incorrect.");
        }
        MessageBoard messageBoard = new MessageBoard();
        Entities.copy(params, messageBoard,
                "userId", "replyUserId", "replyText", "created", "replyDate",
                "ip", "replied", "recommended", "status");
        messageBoard.setSiteId(site.getId());
        String ip = Servlets.getRemoteAddr(request);
        service.insert(messageBoard, userId, ip);
        return Responses.ok();
    }

    @Schema(name = "MessageBoardController.MessageBoardParams", description = "留言参数")
    public static class MessageBoardParams extends MessageBoard {
        private static final long serialVersionUID = 1L;
        @Schema(description = "验证码")
        public String captcha;
        @Schema(description = "验证码TOKEN")
        public String captchaToken;
    }
}
