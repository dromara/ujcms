package com.ujcms.common.web;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Html 工具类
 *
 * @author PONY
 */
public class HtmlParserUtils {
    /**
     * 提取html的纯文本：去除标签，空白（含中文空格 　）折叠为单个空格。
     * 文章摘要与AI筛查/生成共用同一规则，保证预览判定与落库文本一致
     */
    public static String getText(@Nullable String html) {
        if (StringUtils.isBlank(html)) {
            return "";
        }
        return Jsoup.parse(html).body().text().replaceAll("[\\s　]+", " ").trim();
    }

    /**
     * 提取html中图片、视频和链接的URL
     */
    public static List<String> getUrls(@Nullable String html) {
        List<String> urls = new ArrayList<>();
        if (StringUtils.isBlank(html)) {
            return urls;
        }
        Document doc = Jsoup.parseBodyFragment(html);
        doc.select("img,source,video,audio,a").forEach(element -> {
            switch (element.tagName()) {
                case "img","source","audio": {
                    Optional.of(element.attr("src")).filter(url -> !url.isEmpty()).ifPresent(urls::add);
                    break;
                }
                case "video": {
                    // 直接写在 video 上的 src（未用 source 子元素）及封面图 poster
                    Optional.of(element.attr("src")).filter(url -> !url.isEmpty()).ifPresent(urls::add);
                    Optional.of(element.attr("poster")).filter(url -> !url.isEmpty()).ifPresent(urls::add);
                    break;
                }
                case "a": {
                    Optional.of(element.attr("href")).filter(url -> !url.isEmpty()).ifPresent(urls::add);
                    break;
                }
                default:
            }
        });
        return urls;
    }

    private HtmlParserUtils() {
    }
}
