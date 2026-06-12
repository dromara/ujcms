package com.ujcms.cms.ext.web.directive;

import com.ujcms.cms.ext.service.MessageBoardService;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;

import java.io.IOException;
import java.util.Map;

/**
 * 留言板分页 标签
 *
 * @author PONY
 */
public class MessageBoardPageDirective extends MessageBoardListDirective {
    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        doExecute(env, params, loopVars, body, true);
    }

    public MessageBoardPageDirective(MessageBoardService service) {
        super(service);
    }
}
