package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.domain.Dict;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.service.DictService;
import com.ujcms.cms.core.service.ModelService;
import com.ujcms.cms.core.service.args.DictArgs;
import com.ujcms.cms.core.service.args.ModelArgs;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.commons.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 模型列表 标签
 *
 * @author PONY
 */
public class ModelListDirective implements TemplateDirectiveModel {
    /**
     * 站点ID。整型 (Integer)
     */
    public static final String SITE_ID = "siteId";
    /**
     * 共享范围。短整型 (Short)。多个用英文逗号分隔，如'1,2,5'
     */
    public static final String SCOPE = "scope";
    /**
     * 类型。字符串（String）
     */
    private static final String TYPE = "type";

    public static List<Model> query(Map<String, ?> params, Long defaultSiteId, ModelService modelService) {
        ModelArgs args = ModelArgs.of();

        Long siteId = Directives.getLong(params, SITE_ID, defaultSiteId);
        args.scopeSiteId(siteId);
        String type = Directives.getString(params, TYPE);
        args.type(type);

        Directives.handleOrderBy(args.getQueryMap(), params);
        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);

        return modelService.selectList(args, offset, limit);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        List<Model> list = query(params, Frontends.getSiteId(env), modelService);

        loopVars[0] = env.getObjectWrapper().wrap(list);
        body.render(env.getOut());
    }


    private final ModelService modelService;

    public ModelListDirective(ModelService modelService) {
        this.modelService = modelService;
    }
}
