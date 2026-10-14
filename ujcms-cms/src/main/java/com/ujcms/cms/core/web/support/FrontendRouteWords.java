package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.support.SubDirRouteWords;
import com.ujcms.cms.core.support.UrlConstants;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 前台路由保留字。由 {@link SiteRoutingHandlerMapping} 在注册路由时收集每个路由模式的首个字面路径段，
 * 再叠加运行时可配置的栏目、文章 URL 前缀（{@code Config.channelUrl/articleUrl}）。
 * 保留字是从实际注册的路由推导出来的，商业版模块的路由自动计入，无需手工维护清单。
 *
 * @author PONY
 */
public class FrontendRouteWords implements SubDirRouteWords {
    private static final Pattern SEGMENT = Pattern.compile(UrlConstants.SUB_DIR_SEGMENT_REGEX);

    private final Set<String> registered = ConcurrentHashMap.newKeySet();
    private final ConfigService configService;

    public FrontendRouteWords(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * 登记一个路由模式。首段为路径变量或通配符、或含子目录不允许的字符（如 {@code sitemap.xml}）时无冲突可能，忽略
     */
    public void register(String pattern) {
        String word = firstLiteralSegment(pattern);
        if (word != null) {
            registered.add(word);
        }
    }

    @Override
    public Set<String> routeWords() {
        Set<String> words = new HashSet<>(registered);
        Config config = configService.getUnique();
        addIfPresent(words, config.getChannelUrl());
        addIfPresent(words, config.getArticleUrl());
        return words;
    }

    private static void addIfPresent(Set<String> words, @Nullable String url) {
        String word = url != null ? firstLiteralSegment(url) : null;
        if (word != null) {
            words.add(word);
        }
    }

    @Nullable
    static String firstLiteralSegment(String pattern) {
        int start = pattern.startsWith("/") ? 1 : 0;
        int end = pattern.indexOf('/', start);
        String segment = end < 0 ? pattern.substring(start) : pattern.substring(start, end);
        return SEGMENT.matcher(segment).matches() ? segment.toLowerCase(Locale.ROOT) : null;
    }
}
