package com.ofwise.util.web;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Html 工具类
 *
 * @author PONY
 */
public class HtmlUtils {
    /**
     * 提取html中图片、视频和链接的URL
     */
    public static List<String> getUrls(@Nullable String html) {
        List<String> urls = new ArrayList<>();
        if (StringUtils.isBlank(html)) {
            return urls;
        }
        Document doc = Jsoup.parseBodyFragment(html);
        doc.select("img,source,a").forEach(element -> {
            switch (element.tagName()) {
                case "img":
                case "source": {
                    Optional.of(element.attr("src")).filter(url -> !url.isEmpty()).ifPresent(urls::add);
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

    private HtmlUtils() {
    }
}
