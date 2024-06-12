package com.ujcms.cms.ext.web.directive;

import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.Vote;
import com.ujcms.cms.ext.service.VoteService;
import com.ujcms.cms.ext.service.args.VoteArgs;
import com.ujcms.commons.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.springframework.data.domain.Page;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.cms.core.web.support.Directives.*;
import static com.ujcms.commons.db.MyBatis.springPage;

/**
 * 投票列表 标签
 *
 * @author PONY
 */
public class VoteListDirective implements TemplateDirectiveModel {
    /**
     * 是否在投票期限内。布尔型(Boolean)。默认：全部。
     */
    public static final String IS_WITHIN_DATE = "isWithinDate";
    /**
     * 站点ID。整型(Integer)。
     */
    public static final String SITE_ID = "siteId";
    /**
     * 是否获取所有站点的投票。布尔型(Boolean)。默认：false。
     */
    public static final String IS_ALL_SITE = "isAllSite";

    public static void assemble(VoteArgs args, Map<String, ?> params, Long defaultSiteId) {
        Long siteId = getLong(params, SITE_ID);
        // 不获取所有站点，则给默认站点ID
        if (siteId == null && !getBoolean(params, IS_ALL_SITE, false)) {
            siteId = defaultSiteId;
        }
        args.siteId(siteId);
        Optional.ofNullable(getBoolean(params, IS_WITHIN_DATE)).ifPresent(args::withinDate);
        args.enabled(true);
        Directives.handleOrderBy(args.getQueryMap(), params, "order_desc,id_desc");
    }

    protected void doExecute(Environment env, Map<String, TemplateModel> params, TemplateModel[] loopVars,
                             TemplateDirectiveBody body, boolean isPage) throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        VoteArgs args = VoteArgs.of(Directives.getQueryMap(params));
        assemble(args, params, Frontends.getSiteId(env));

        if (isPage) {
            int page = Directives.getPage(params, env);
            int pageSize = Directives.getPageSize(params, env);
            Page<Vote> pagedList = springPage(service.selectPage(args, page, pageSize));
            Directives.setTotalPages(pagedList.getTotalPages());
            loopVars[0] = env.getObjectWrapper().wrap(pagedList);
        } else {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            List<Vote> list = service.selectList(args, offset, limit);
            loopVars[0] = env.getObjectWrapper().wrap(list);
        }
        body.render(env.getOut());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        doExecute(env, params, loopVars, body, false);
    }

    private final VoteService service;

    public VoteListDirective(VoteService service) {
        this.service = service;
    }
}
