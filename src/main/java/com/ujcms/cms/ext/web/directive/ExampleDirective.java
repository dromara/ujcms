package com.ujcms.cms.ext.web.directive;

import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.Example;
import com.ujcms.cms.ext.service.ExampleService;
import com.ujcms.commons.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;

import java.io.IOException;
import java.util.Map;

/**
 * 演示对象 标签
 * <p>
 * 根据ID获取演示对象
 * <p>
 * Freemarker官方自定义标签文档：https://freemarker.apache.org/docs/pgui_datamodel_directive.html
 *
 * @author PONY
 */
public class ExampleDirective implements TemplateDirectiveModel {
    private static final String ID = "id";

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        // 检查返回参数是否存在
        Freemarkers.requireLoopVars(loopVars);
        // 检查标签体是否存在
        Freemarkers.requireBody(body);
        // 获取id参数
        Long id = Directives.getLongRequired(params, ID);
        // 查询数据
        Example bean = service.select(id);
        // 将查询结果放入返回参数中
        loopVars[0] = env.getObjectWrapper().wrap(bean);
        // 执行标签体
        body.render(env.getOut());
    }

    private final ExampleService service;

    public ExampleDirective(ExampleService service) {
        this.service = service;
    }
}
