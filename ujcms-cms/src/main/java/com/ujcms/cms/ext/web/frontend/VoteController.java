package com.ujcms.cms.ext.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.web.support.FrontendController;
import com.ujcms.cms.ext.domain.Vote;
import com.ujcms.cms.ext.service.VoteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 前台投票 Controller
 *
 * @author PONY
 */
@FrontendController("frontendVoteController")
public class VoteController {
    private final VoteService service;

    public VoteController(VoteService service) {
        this.service = service;
    }

    private static final String TEMPLATE = "sys_vote";
    private static final String TEMPLATE_SHOW = "sys_vote_show";
    private static final String TEMPLATE_RESULT = "sys_vote_result";

    @GetMapping("/vote")
    public String list(Site site) {
        return site.assembleTemplate(TEMPLATE);
    }

    @GetMapping("/vote/{id}")
    public String show(Site site, @PathVariable Long id, Map<String, Object> modelMap) {
        handleVote(id, modelMap);
        return site.assembleTemplate(TEMPLATE_SHOW);
    }

    @GetMapping("/vote/{id}/result")
    public String result(Site site, @PathVariable Long id, Map<String, Object> modelMap) {
        handleVote(id, modelMap);
        return site.assembleTemplate(TEMPLATE_RESULT);
    }

    private void handleVote(Long id, Map<String, Object> modelMap) {
        Vote vote = service.select(id);
        com.ujcms.cms.ext.web.api.VoteController.validateVote(id, vote);
        modelMap.put("vote", vote);
    }
}
