package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.service.ConfigService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * URL重写 拦截器。允许用户自定义 /channel、/article 的动态地址。
 *
 * @author PONY
 */
public class UrlRedirectInterceptor implements HandlerInterceptor {
    private final ConfigService configService;

    public UrlRedirectInterceptor(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws ServletException, IOException {
        Config config = configService.getUnique();
        String channel = config.getChannelUrl();
        String article = config.getArticleUrl();
        String servletPath = request.getServletPath();

        String channelUri = channel + "/[\\w-]+";
        String channelSubUri = "/[\\w-]+" + channel + "/[\\w-]+";
        if (rewrite(request, response, servletPath, channelUri, channelSubUri, UrlConstants.CHANNEL, channel)) {
            return false;
        }

        String articleUri = article + "/[\\d]+(/[\\w-]+)?";
        String articleSubUri = "/[\\w-]+" + article + "/[\\d]+(/[\\w-]+)?";
        return !rewrite(request, response, servletPath, articleUri, articleSubUri, UrlConstants.ARTICLE, article);
    }

    private boolean rewrite(HttpServletRequest request, HttpServletResponse response, String servletPath,
                            String uri, String subUri, String orig, @Nullable String changed)
            throws ServletException, IOException {
        if (StringUtils.isBlank(changed) || orig.equals(changed)) {
            return false;
        }
        if (Pattern.compile(uri).matcher(servletPath).matches()) {
            String to = orig + servletPath.substring(changed.length());
            request.getRequestDispatcher(to).forward(request, response);
            return true;
        }
        if (Pattern.compile(subUri).matcher(servletPath).matches()) {
            int index = servletPath.indexOf(changed);
            String to = servletPath.substring(0, index) + orig + servletPath.substring(index + changed.length());
            request.getRequestDispatcher(to).forward(request, response);
            return true;
        }
        return false;
    }


}
