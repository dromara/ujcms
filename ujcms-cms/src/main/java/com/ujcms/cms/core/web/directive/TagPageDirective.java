package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.service.TagService;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;

import java.io.IOException;
import java.util.Map;

/**
 * TAG列表 标签
 *
 * @author PONY
 */
public class TagPageDirective extends TagListDirective {
    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        doExecute(env, params, loopVars, body, true);
    }

    public TagPageDirective(TagService service) {
        super(service);
    }
}
