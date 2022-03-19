package com.ujcms.core.web.directive;

import com.ofwise.util.freemarker.Freemarkers;
import com.ofwise.util.web.Strings;
import com.ujcms.core.support.Anchor;
import com.ujcms.core.web.support.Directives;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Anchor FreeMarker标签。用于生成 html a 标签。
 *
 * @author liufang
 */
public class AnchorDirective implements TemplateDirectiveModel {
    public static final String BEAN = "bean";
    public static final String TARGET = "target";
    public static final String TITLE = "title";
    public static final String LENGTH = "length";
    public static final String APPEND = "append";

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars,
                        TemplateDirectiveBody body) throws TemplateException, IOException {
        Anchor anchor = Freemarkers.getObject(params, BEAN, Anchor.class);
        if (anchor == null) {
            return;
        }

        String target = Directives.getString(params, TARGET);
        if (StringUtils.isBlank(target) && anchor.getTargetBlank()) {
            target = "_blank";
        }
        String title = Optional.ofNullable(Directives.getString(params, TITLE)).filter(StringUtils::isNotBlank)
                .orElseGet(anchor::getName);

        String append = Optional.ofNullable(Directives.getString(params, APPEND)).orElse("...");
        Integer length = Directives.getInteger(params, LENGTH);

        StringBuilder buff = new StringBuilder();
        buff.append("<a href=\"").append(HtmlUtils.htmlEscape(anchor.getUrl())).append("\"");
        buff.append(" title=\"").append(HtmlUtils.htmlEscape(title)).append("\"");
        if (StringUtils.isNotBlank(target)) {
            buff.append(" target=\"").append(HtmlUtils.htmlEscape(target)).append("\"");
        }
        // 插入其它属性
        for (Object obj : params.entrySet()) {
            Map.Entry<String, TemplateModel> entry = (Map.Entry<String, TemplateModel>) obj;
            String name = entry.getKey();
            if (StringUtils.equalsAnyIgnoreCase(name, BEAN, TARGET, TITLE, LENGTH, APPEND)) {
                continue;
            }
            String value = Optional.ofNullable(Freemarkers.getString(entry.getValue())).map(HtmlUtils::htmlEscape)
                    .orElse("");
            buff.append(" ").append(name).append("=\"").append(value).append("\"");
        }
        buff.append(">");
        env.getOut().write(buff.toString());
        if (body == null) {
            String innerTitle = anchor.getName();
            if (length != null && length > 0) {
                innerTitle = Strings.substring(innerTitle, length, append);
            }
            env.getOut().write(HtmlUtils.htmlEscape(innerTitle));
        } else {
            body.render(env.getOut());
        }
        env.getOut().write("</a>");
    }
}
