package com.ujcms.cms.ext.web.directive;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;

import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.FormType;
import com.ujcms.cms.ext.service.FormTypeService;
import com.ujcms.cms.ext.service.args.FormTypeArgs;
import com.ujcms.commons.freemarker.Freemarkers;

import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;

/**
 * 表单列表 标签
 *
 * @author PONY
 */
public class FormTypeListDirective implements TemplateDirectiveModel {
    /**
     * 类型ID。必选项。整型(Integer)。
     */
    public static final String SITE_ID = "siteId";
    /**
     * 模式。短整型(Short)。0:前台游客,1:前台登录用户,2:仅后台用户。多个用英文逗号分隔，如 '0,1,2'；全部使用空串 ''。默认：'0,1'
     */
    public static final String MODE = "mode";
    /**
     * 是否前台可查看。布尔型（Boolean）。true: 可查看, false: 不可查看。默认：全部
     */
    public static final String IS_VIEWABLE = "isViewable";
    /**
     * 是否启用。布尔型（Boolean）。可选值：all(全部), false(禁用)。默认：启用
     */
    public static final String IS_ENABLED = "isEnabled";

    public static void assemble(FormTypeArgs args, Map<String, ?> params, Long defaultSiteId) {
        Long siteId = Directives.getLong(params, SITE_ID, defaultSiteId);
        args.siteId(siteId);
        List<Short> modes = Directives.getShorts(params, MODE);
        if (modes == null) {
            args.mode(List.of((short) 0, (short) 1));
        } else if (!modes.isEmpty()) {
            args.mode(modes);
        }
        Optional.ofNullable(Directives.getBoolean(params, IS_VIEWABLE)).ifPresent(args::viewable);
        Optional.ofNullable(Directives.getBooleanDefault(params, IS_ENABLED, true)).ifPresent(args::enabled);
        Directives.handleOrderBy(args.getQueryMap(), params);
    }

    protected void doExecute(Environment env, Map<String, TemplateModel> params, TemplateModel[] loopVars,
            TemplateDirectiveBody body) throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        FormTypeArgs args = FormTypeArgs.of(Directives.getQueryMap(params));
        assemble(args, params, Frontends.getSiteId(env));

        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        List<FormType> list = service.selectList(args, offset, limit);
        loopVars[0] = env.getObjectWrapper().wrap(list);
        body.render(env.getOut());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        doExecute(env, params, loopVars, body);
    }

    private final FormTypeService service;

    public FormTypeListDirective(FormTypeService service) {
        this.service = service;
    }
}
