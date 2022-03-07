package com.ofwise.util.freemarker;

import com.ofwise.util.web.PageUrlResolver;
import com.ujcms.core.web.support.Directives;
import freemarker.core.Environment;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * FreeMarker翻页方法
 *
 * <ul>
 * <li>普通的动态地址：      /channel/news             -> /channel/news?page=2
 * <li>带查询条件的动态地址：/channel/news?type=comedy -> /channel/news/?type=comedy&page=3
 * <li>静态页目录地址：      /news/                    -> /news/index_2.html
 * <li>静态页目录地址：      /news/index_2.html        -> /news/index_3.html
 * </ul>
 *
 * @author liufang
 */
public class PagingMethod implements TemplateMethodModelEx {
    @Override
    public Object exec(List args) throws TemplateModelException {
        Integer page = null;
        if (args.size() > 0) {
            TemplateModel arg0 = (TemplateModel) args.get(0);
            page = Freemarkers.getInteger(arg0);
        }
        Environment env = Environment.getCurrentEnvironment();
        String queryString = StringUtils.trim(Directives.getQueryString(env));
        if (StringUtils.isNotBlank(queryString)) {
            // 删除原有page。page=3&page=4&page=10&page=0
            queryString = pattern.matcher(queryString).replaceAll("");
        }
        PageUrlResolver pageUrlResolver = Directives.getPageUrlResolver(env);
        if (pageUrlResolver != null) {
            if (StringUtils.isNotBlank(queryString)) {
                return pageUrlResolver.getDynamicUrl(page != null ? page : 1) + "?" + queryString;
            }
            return pageUrlResolver.getUrl(page != null ? page : 1);
        }
        String url = Directives.getUrl(env);
        if (page == null || page <= 1) {
            if (StringUtils.isNotBlank(queryString)) {
                return url + "?" + queryString;
            }
            return url;
        }
        if (StringUtils.isNotBlank(queryString)) {
            return url + "?" + queryString + "&" + param + "=" + page;
        }
        return url + "?" + param + "=" + page;
    }

    private String param;
    private Pattern pattern;

    public PagingMethod(String param) {
        this.param = param;
        this.pattern = Pattern.compile("&*\\s*" + param + "\\s*=[^&]*");
    }
}
