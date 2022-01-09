package com.ofwise.util.freemarker;

import com.ofwise.util.web.Strings;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;

import java.util.List;

/**
 * FreeMarker字符串截断方法。英文字符算半个，中文字符算一个。
 *
 * @author liufang
 */
public class SubstringMethod implements TemplateMethodModelEx {

    @Override
    public Object exec(List args) throws TemplateModelException {
        // 参数最小个数
        int minArgsSize = 2;
        if (args.size() < minArgsSize) {
            throw new TemplateModelException("arg0, arg1 is required!");
        }
        TemplateModel arg0 = (TemplateModel) args.get(0);
        String text = Freemarkers.getString(arg0);

        TemplateModel arg1 = (TemplateModel) args.get(1);
        Integer length = Freemarkers.getIntegerRequired(arg1, "arg1");

        String append = null;
        if (args.size() > minArgsSize) {
            TemplateModel arg2 = (TemplateModel) args.get(2);
            append = Freemarkers.getString(arg2);
        }
        return Strings.substring(text, length, append);
    }

}
