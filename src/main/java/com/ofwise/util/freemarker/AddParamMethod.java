package com.ofwise.util.freemarker;

import com.ujcms.core.web.support.Directives;
import freemarker.core.Environment;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;


/**
 * Freemarker 增加参数方法
 *
 * <p>
 * 增加或替换参数，可以有多个参数，参数值可以是数组。同时去除分页和排序参数。
 * addParam('http://www.abc.com/news','abc',123);addParam('abc',123,456);
 * <p>
 *
 * @author liufang
 */
public class AddParamMethod implements TemplateMethodModelEx {
    @Override
    public Object exec(List args) throws TemplateModelException {
        int argsSize = args.size();
        int minArgsSize = 3;
        if (argsSize < minArgsSize) {
            throw new TemplateModelException("Arguments size must greater or equal to " + minArgsSize);
        }
        Environment env = Environment.getCurrentEnvironment();
        String envUrl = Directives.getUrl(env);
        // 获取URL
        String url = Optional.ofNullable(Freemarkers.getString((TemplateModel) args.get(0))).orElse(envUrl);
        // 获取动态URL
        String dynamicUrl = Optional.ofNullable(Freemarkers.getString((TemplateModel) args.get(1))).orElse(envUrl);
        // 获取参数名称
        String name = Freemarkers.getStringRequired((TemplateModel) args.get(2), "arg2");
        // 获取参数值，可以有多个。
        List<String> values = new ArrayList<>(argsSize - 1);
        for (int i = minArgsSize; i < argsSize; i++) {
            String value = Freemarkers.getString((TemplateModel) args.get(i));
            if (StringUtils.isNotBlank(value)) {
                values.add(value);
            }
        }

        String queryString = StringUtils.trim(Directives.getQueryString(env));
        if (StringUtils.isNotBlank(queryString)) {
            // 删除原有param
            Pattern pattern = Pattern.compile("&*\\s*" + name + "\\s*=[^&]*");
            queryString = pattern.matcher(queryString).replaceAll("");
            queryString = pagePattern.matcher(queryString).replaceAll("");
        }
        if (StringUtils.isBlank(queryString) && values.isEmpty()) {
            return url;
        }
        StringBuilder buff = new StringBuilder(dynamicUrl).append("?");
        if (StringUtils.isNotBlank(queryString)) {
            buff.append(StringUtils.removeStart(queryString, "&"));
            if (!values.isEmpty()) {
                buff.append("&");
            }
        }
        // 增加参数
        for (Iterator<String> it = values.iterator(); it.hasNext(); ) {
            buff.append(name).append("=").append(it.next());
            if (it.hasNext()) {
                buff.append("&");
            }
        }
        return buff.toString();
    }

    private Pattern pagePattern;

    public AddParamMethod(String page) {
        this.pagePattern = Pattern.compile("&*\\s*" + page + "\\s*=[^&]*");
    }
}
