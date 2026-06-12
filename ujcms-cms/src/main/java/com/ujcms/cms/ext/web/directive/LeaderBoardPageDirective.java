package com.ujcms.cms.ext.web.directive;

import java.io.IOException;
import java.util.Map;

import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.ext.service.LeaderBoardService;

import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;

/**
 * 组织文章排行榜分页 标签
 *
 * @author PONY
 */
public class LeaderBoardPageDirective extends LeaderBoardListDirective {
    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        doExecute(env, params, loopVars, body, true);
    }

    public LeaderBoardPageDirective(LeaderBoardService service, SiteService siteService) {
        super(service, siteService);
    }
}
