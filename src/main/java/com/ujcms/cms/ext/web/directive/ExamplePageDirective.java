package com.ujcms.cms.ext.web.directive;

import static com.ujcms.commons.db.MyBatis.springPage;

import java.io.IOException;
import java.util.Map;

import org.springframework.data.domain.Page;

import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.Example;
import com.ujcms.cms.ext.service.ExampleService;
import com.ujcms.cms.ext.service.args.ExampleArgs;
import com.ujcms.commons.freemarker.Freemarkers;

import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;

/**
 * 示例分页 标签
 * <p>
 * Freemarker官方自定义标签文档：https://freemarker.apache.org/docs/pgui_datamodel_directive.html
 *
 * @author PONY
 */
public class ExamplePageDirective implements TemplateDirectiveModel {
    /**
     * 名称。字符串(String)。
     */
    public static final String NAME = "name";

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        // 检查返回参数是否存在
        Freemarkers.requireLoopVars(loopVars);
        // 检查标签体是否存在
        Freemarkers.requireBody(body);
        // 获取标签其它查询参数。非必须，可用 `ExampleArgs args = ExampleArgs.of();` 代替。
        ExampleArgs args = ExampleArgs.of(Directives.getQueryMap(params));
        // 获取标签的name参数
        String name = Directives.getString(params, NAME);
        // 加入name查询条件
        args.name(name);
        // 第几页
        int page = Directives.getPage(params, env);
        // 每页条数
        int pageSize = Directives.getPageSize(params, env);
        // 查询数据
        Page<Example> pagedList = springPage(service.selectPage(args, page, pageSize));
        // 将查询结果放入返回参数中
        loopVars[0] = env.getObjectWrapper().wrap(pagedList);
        // 执行标签体
        body.render(env.getOut());
    }

    private final ExampleService service;

    public ExamplePageDirective(ExampleService service) {
        this.service = service;
    }
}
