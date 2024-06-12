package com.ujcms.cms.ext.web.directive;

import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.Survey;
import com.ujcms.cms.ext.service.SurveyService;
import com.ujcms.commons.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;

import java.io.IOException;
import java.util.Map;

/**
 * 调查问卷 标签
 *
 * @author PONY
 */
public class SurveyDirective implements TemplateDirectiveModel {
    private static final String ID = "id";

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);
        Long id = Directives.getLongRequired(params, ID);

        Survey bean = service.select(id);
        loopVars[0] = env.getObjectWrapper().wrap(bean);
        body.render(env.getOut());
    }

    private final SurveyService service;

    public SurveyDirective(SurveyService service) {
        this.service = service;
    }
}
