package com.ujcms.cms.ext.web.directive;

import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.Form;
import com.ujcms.cms.ext.domain.FormType;
import com.ujcms.cms.ext.service.FormService;
import com.ujcms.cms.ext.service.FormTypeService;
import com.ujcms.cms.ext.service.args.FormArgs;
import com.ujcms.commons.freemarker.Freemarkers;
import com.ujcms.commons.web.exception.Http400Exception;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.springframework.data.domain.Page;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.commons.db.MyBatis.springPage;

/**
 * 表单列表 标签
 *
 * @author PONY
 */
public class FormListDirective implements TemplateDirectiveModel {
    /**
     * 类型ID。必选项。整型(Integer)。
     */
    public static final String TYPE_ID = "typeId";
    /**
     * 组织ID。整型(Integer)。
     */
    public static final String ORG_ID = "orgId";
    /**
     * 用户ID。整型(Integer)。
     */
    public static final String USER_ID = "userId";

    public static void assemble(FormArgs args, Map<String, ?> params, FormTypeService typeService) {
        Long typeId = Optional.ofNullable(Directives.getLong(params, TYPE_ID))
                .orElseThrow(() -> new Http400Exception("typeId is required."));
        FormType formType = typeService.select(typeId);
        if (formType != null && Boolean.FALSE.equals(formType.getViewable())) {
            throw new Http400Exception("FormType is not viewable. ID: " + formType.getId());
        }
        args.typeId(typeId);
        Optional.ofNullable(Directives.getLong(params, ORG_ID)).ifPresent(args::orgId);
        Optional.ofNullable(Directives.getLong(params, USER_ID)).ifPresent(args::userId);
        args.status(Collections.singletonList(Form.STATUS_REVIEWED));
        Directives.handleOrderBy(args.getQueryMap(), params, "order_desc,id_desc");
    }

    protected void doExecute(Environment env, Map<String, TemplateModel> params, TemplateModel[] loopVars,
                             TemplateDirectiveBody body, boolean isPage) throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        FormArgs args = FormArgs.of(Directives.getQueryMap(params));
        assemble(args, params, typeService);

        if (isPage) {
            int page = Directives.getPage(params, env);
            int pageSize = Directives.getPageSize(params, env);
            Page<Form> pagedList = springPage(service.selectPage(args, page, pageSize));
            Directives.setTotalPages(pagedList.getTotalPages());
            loopVars[0] = env.getObjectWrapper().wrap(pagedList);
        } else {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            List<Form> list = service.selectList(args, offset, limit);
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

    private final FormService service;
    private final FormTypeService typeService;

    public FormListDirective(FormService service, FormTypeService typeService) {
        this.service = service;
        this.typeService = typeService;
    }
}
