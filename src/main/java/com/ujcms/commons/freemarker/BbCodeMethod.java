package com.ujcms.commons.freemarker;

import com.ujcms.commons.web.Strings;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;

import java.util.List;
import java.util.Optional;

/**
 * bbcode转换成html
 *
 * @author liufang
 */
public class BbCodeMethod implements TemplateMethodModelEx {
    @SuppressWarnings("rawtypes")
    @Override
    public Object exec(List args) throws TemplateModelException {
        String text;
        if (args.size() > 0) {
            TemplateModel arg0 = (TemplateModel) args.get(0);
            text = Freemarkers.getString(arg0);
        } else {
            throw new TemplateModelException("arg0 is missing.");
        }
        return Optional.ofNullable(Strings.bbcode(text)).orElse("");
    }
}
