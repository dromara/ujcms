package com.ujcms.cms.ext.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.cms.ext.domain.Vote;
import com.ujcms.cms.ext.service.VoteService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 前台投票 Controller
 *
 * @author PONY
 */
@Controller("frontendVoteController")
public class VoteController {
    private final SiteResolver siteResolver;
    private final VoteService service;

    public VoteController(SiteResolver siteResolver, VoteService service) {
        this.siteResolver = siteResolver;
        this.service = service;
    }

    private static final String TEMPLATE = "sys_vote";
    private static final String TEMPLATE_SHOW = "sys_vote_show";
    private static final String TEMPLATE_RESULT = "sys_vote_result";

    @GetMapping({"/vote", "/{subDir:[\\w-]+}/vote"})
    public String list(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(TEMPLATE);
    }

    @GetMapping({"/vote/{id}", "/{subDir:[\\w-]+}/vote/{id}"})
    public String show(@PathVariable(required = false) String subDir, @PathVariable Long id,
                       HttpServletRequest request, Map<String, Object> modelMap) {
        Site site = siteResolver.resolve(request, subDir);
        handleVote(id, modelMap);
        return site.assembleTemplate(TEMPLATE_SHOW);
    }

    @GetMapping({"/vote/{id}/result", "/{subDir:[\\w-]+}/vote/{id}/result"})
    public String result(@PathVariable(required = false) String subDir, @PathVariable Long id,
                         HttpServletRequest request, Map<String, Object> modelMap) {
        Site site = siteResolver.resolve(request, subDir);
        handleVote(id, modelMap);
        return site.assembleTemplate(TEMPLATE_RESULT);
    }

    private void handleVote(Long id, Map<String, Object> modelMap) {
        Vote vote = service.select(id);
        com.ujcms.cms.ext.web.api.VoteController.validateVote(id, vote);
        modelMap.put("vote", vote);
    }
}
