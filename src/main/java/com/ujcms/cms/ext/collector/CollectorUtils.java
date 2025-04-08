package com.ujcms.cms.ext.collector;

import com.ujcms.commons.web.Strings;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 采集工具类
 *
 * @author PONY
 */
public class CollectorUtils {
    public static List<String> assembleListUrls(String listUrls, int pageBegin, int pageEnd, boolean listDesc) {
        List<String> list = new ArrayList<>();
        String[] urls = Strings.splitByLine(listUrls);
        for (String url : urls) {
            if (!url.contains(PLACEHOLDER)) {
                list.add(url);
                continue;
            }
            for (int begin = pageBegin; begin <= pageEnd; begin++) {
                list.add(StringUtils.replaceOnce(url, PLACEHOLDER, String.valueOf(begin)));
            }
        }
        if (listDesc) {
            Collections.reverse(list);
        }
        return list;
    }


    public static List<String> fetchItemUrls(String url, String userAgent, String charset, String listAreaPattern,
                                             String itemUrlPattern, boolean itemUrlReg, boolean itemUrlJs)
            throws URISyntaxException, IOException {
        String listArea = fetchListArea(url, userAgent, charset, listAreaPattern);
        return matchItemUrls(url, listArea, itemUrlPattern, itemUrlReg, itemUrlJs);
    }

    public static String fetchListArea(String url, String userAgent, String charset, String listAreaPattern)
            throws IOException {
        String text = fetchContent(url, userAgent, charset);
        return matchSingle(text, listAreaPattern, false, false);
    }

    public static List<String> matchItemArea(String listArea, String itemAreaPattern, boolean itemAreaReg) {
        return matchMultiple(listArea, itemAreaPattern, itemAreaReg, false);
    }

    public static List<String> matchItemUrls(String url, String listArea, String itemUrlPattern,
                                             boolean itemUrlReg, boolean itemUrlJs) throws URISyntaxException {
        List<String> results = matchMultiple(listArea, itemUrlPattern, itemUrlReg, itemUrlJs);
        List<String> resolved = new ArrayList<>(results.size());
        URI uri = new URI(StringEscapeUtils.unescapeHtml4(url));
        for (String result : results) {
            resolved.add(uri.resolve(result).toString());
        }
        return resolved;
    }

    public static List<ItemAreaUrl> matchItemAreaUrls(String url, String listArea,
                                                      String itemAreaPattern, boolean itemAreaReg,
                                                      String itemUrlPattern, boolean itemUrlReg, boolean itemUrlJs,
                                                      String itemUrlFilter) throws URISyntaxException {
        if (StringUtils.isBlank(itemAreaPattern)) {
            return matchItemUrls(url, listArea, itemUrlPattern, itemUrlReg, itemUrlJs)
                    .stream().map(item -> new ItemAreaUrl(item, null))
                    .collect(Collectors.toList());
        }
        List<String> itemAreas = matchItemArea(listArea, itemAreaPattern, itemAreaReg);
        List<ItemAreaUrl> results = new ArrayList<>(itemAreas.size());
        URI uri = new URI(StringEscapeUtils.unescapeHtml4(url));
        List<Pattern> filters = CollectorUtils.compileFilter(itemUrlFilter);
        for (String itemArea : itemAreas) {
            String itemUrl = matchSingle(itemArea, itemUrlPattern, itemUrlReg, itemUrlJs);
            itemUrl = applyFilter(itemUrl, filters);
            if (StringUtils.isNotBlank(itemUrl)) {
                results.add(new ItemAreaUrl(uri.resolve(itemUrl).toString(), itemArea));
            }
        }
        return results;
    }

    public static String fetchContent(String url, String userAgent, String charset) throws IOException {
        HttpClientBuilder builder = HttpClientBuilder.create();
        builder.setUserAgent(userAgent);
        try (CloseableHttpClient client = builder.build()) {
            HttpGet httpGet = new HttpGet(url);
            try (CloseableHttpResponse response = client.execute(httpGet)) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    return EntityUtils.toString(entity, charset);
                } else {
                    return "Status Code: " + response.getStatusLine().getStatusCode();
                }
            }
        }
    }

    public static List<Pattern> compileFilter(@Nullable String filter) {
        if (StringUtils.isBlank(filter)) {
            return Collections.emptyList();
        }
        List<Pattern> patterns = new ArrayList<>();
        for (String regex : Strings.splitByLine(filter)) {
            if (StringUtils.isNotBlank(regex)) {
                patterns.add(Pattern.compile(regex));
            }
        }
        return patterns;
    }

    public static String applyFilter(String text, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            text = Strings.filter(text, pattern);
        }
        return text;
    }


    public static String matchSingle(@Nullable String text, @Nullable String pattern, boolean isReg, boolean isJs) {
        List<String> results = match(text, pattern, isReg, isJs, false);
        if (results.isEmpty()) {
            return StringUtils.EMPTY;
        }
        return results.get(0);
    }

    public static List<String> matchMultiple(@Nullable String text, @Nullable String pattern,
                                             boolean isReg, boolean isJs) {
        return match(text, pattern, isReg, isJs, true);
    }

    public static List<String> match(@Nullable String text, @Nullable String pattern,
                                     boolean isReg, boolean isJs, boolean isMulti) {
        return match(text, pattern, isReg, isJs, isMulti ? Integer.MAX_VALUE : 1);
    }

    private static List<String> match(@Nullable String text, @Nullable String pattern,
                                      boolean isReg, boolean isJs, int max) {
        if (text == null) {
            return Collections.emptyList();
        }
        if (StringUtils.isBlank(pattern)) {
            return Collections.singletonList(isJs ? StringEscapeUtils.unescapeEcmaScript(text) : text);
        }
        text = Strings.standardLineBreak(text);
        pattern = Strings.standardLineBreak(pattern);
        if (isReg) {
            return findByReg(text, pattern, isJs, max);
        } else {
            return findByPlaceholder(text, pattern, isJs, max);
        }
    }

    private static List<String> findByReg(String text, String regex, boolean isJs, int max) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        List<String> results = new ArrayList<>();
        for (int i = 0; i < max && matcher.find(); i += 1) {
            String result = matcher.group(1).trim();
            if (StringUtils.isNotBlank(result)) {
                results.add(isJs ? StringEscapeUtils.unescapeEcmaScript(result) : result);
            }
        }
        return results;
    }

    private static List<String> findByPlaceholder(String text, String pattern, boolean isJs, int max) {
        int index = pattern.indexOf(PLACEHOLDER);
        if (index == -1) {
            return Collections.emptyList();
        }

        String open = pattern.substring(0, index);
        String close = pattern.substring(index + PLACEHOLDER.length());
        List<String> results = new ArrayList<>();
        int begin = 0;
        for (int i = 0; i < max && (begin = text.indexOf(open, begin)) != -1; i += 1) {
            begin += open.length();
            int end = text.length();
            if (!close.isEmpty()) {
                end = text.indexOf(close, begin);
                if (end == -1) {
                    return results;
                }
            }
            String result = text.substring(begin, end).trim();
            if (StringUtils.isNotBlank(result)) {
                results.add(isJs ? StringEscapeUtils.unescapeEcmaScript(result) : result);
            }
            begin = end + close.length();
        }
        return results;
    }

    public static long randomBetween(long min, long max) {
        if (max >= min && min > 0) {
            double random = Math.random();
            return (long) (random * max - random * min + min);
        } else {
            return 0;
        }
    }

    public static final String PLACEHOLDER = "(*)";

    private CollectorUtils() {
        throw new IllegalStateException("Utility class");
    }
}
