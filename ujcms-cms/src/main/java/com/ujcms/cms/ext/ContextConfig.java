package com.ujcms.cms.ext;

import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.ext.service.*;
import com.ujcms.cms.ext.web.directive.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;

/**
 * ContextConfig
 *
 * @author PONY
 */
@Configuration
@MapperScan("com.ujcms.cms.ext.mapper")
public class ContextConfig implements InitializingBean {
    private final SiteService siteService;
    private final VoteService voteService;
    private final CommentService commentService;
    private final MessageBoardService messageBoardService;
    private final MessageBoardTypeService messageBoardTypeService;
    private final LeaderBoardService leaderBoardService;
    private final LinkService linkService;
    private final LinkGroupService linkGroupService;
    private final ExampleService exampleService;
    private final freemarker.template.Configuration configuration;

    public ContextConfig(SiteService siteService, VoteService voteService, CommentService commentService,
                         MessageBoardService messageBoardService,
                         MessageBoardTypeService messageBoardTypeService,
                         LeaderBoardService leaderBoardService, LinkService linkService,
                         LinkGroupService linkGroupService,
                         ExampleService exampleService, freemarker.template.Configuration configuration) {
        this.siteService = siteService;
        this.voteService = voteService;
        this.commentService = commentService;
        this.messageBoardService = messageBoardService;
        this.messageBoardTypeService = messageBoardTypeService;
        this.leaderBoardService = leaderBoardService;
        this.linkService = linkService;
        this.linkGroupService = linkGroupService;
        this.exampleService = exampleService;
        this.configuration = configuration;
    }

    @Override
    public void afterPropertiesSet() {
        configuration.setSharedVariable("Vote", new VoteDirective(voteService));
        configuration.setSharedVariable("VoteList", new VoteListDirective(voteService));
        configuration.setSharedVariable("VotePage", new VotePageDirective(voteService));
        configuration.setSharedVariable("CommentList", new CommentListDirective(commentService));
        configuration.setSharedVariable("CommentPage", new CommentPageDirective(commentService));
        configuration.setSharedVariable("MessageBoardList", new MessageBoardListDirective(messageBoardService));
        configuration.setSharedVariable("MessageBoardPage", new MessageBoardPageDirective(messageBoardService));
        configuration.setSharedVariable("MessageBoardTypeList",
                new MessageBoardTypeListDirective(messageBoardTypeService));
        configuration.setSharedVariable("LinkList",
                new LinkListDirective(linkService, linkGroupService));
        configuration.setSharedVariable("LinkGroupList",
                new LinkGroupListDirective(linkGroupService));
        configuration.setSharedVariable("LeaderBoardList",
                new LeaderBoardListDirective(leaderBoardService, siteService));
        configuration.setSharedVariable("LeaderBoardPage",
                new LeaderBoardPageDirective(leaderBoardService, siteService));
        configuration.setSharedVariable("Example", new ExampleDirective(exampleService));
        configuration.setSharedVariable("ExampleList", new ExampleListDirective(exampleService));
        configuration.setSharedVariable("ExamplePage", new ExamplePageDirective(exampleService));
    }
}
