package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.domain.Tag;
import com.ujcms.cms.core.service.TagService;
import com.ujcms.cms.core.service.args.TagArgs;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
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
 * TAG列表 标签
 *
 * @author PONY
 */
public class TagListDirective implements TemplateDirectiveModel {
    /**
     * 站点ID。整型(Integer)。
     */
    public static final String SITE_ID = "siteId";
    /**
     * 名称。字符串(String)。
     */
    public static final String NAME = "name";
    /**
     * 是否被引用。布尔型(Boolean)。可选值：all(全部), false(禁用)。默认值：启用。
     */
    public static final String IS_REFERRED = "isReferred";
    /**
     * 是否获取所有站点的Tag。布尔型(Boolean)。默认：false。
     */
    public static final String IS_ALL_SITE = "isAllSite";

    public static void assemble(TagArgs args, Map<String, ?> params, Long defaultSiteId) {
        Long siteId = getLong(params, SITE_ID);
        // 不获取所有站点，则给默认站点ID
        if (siteId == null && !getBoolean(params, IS_ALL_SITE, false)) {
            siteId = defaultSiteId;
        }
        args.siteId(siteId);
        Optional.ofNullable(Directives.getBooleanDefault(params, IS_REFERRED, true)).ifPresent(args::isReferred);
        Optional.ofNullable(Directives.getString(params, NAME)).ifPresent(args::name);
        Directives.handleOrderBy(args.getQueryMap(), params, "refers_desc,id_desc");
    }

    protected void doExecute(Environment env, Map<String, TemplateModel> params, TemplateModel[] loopVars,
                             TemplateDirectiveBody body, boolean isPage) throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        TagArgs args = TagArgs.of(Directives.getQueryMap(params));
        assemble(args, params, Frontends.getSiteId(env));

        if (isPage) {
            int page = Directives.getPage(params, env);
            int pageSize = Directives.getPageSize(params, env);
            Page<Tag> pagedList = springPage(service.selectPage(args, page, pageSize));
            Directives.setTotalPages(pagedList.getTotalPages());
            loopVars[0] = env.getObjectWrapper().wrap(pagedList);
        } else {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            List<Tag> list = service.selectList(args, offset, limit);
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

    private final TagService service;

    public TagListDirective(TagService service) {
        this.service = service;
    }
}
