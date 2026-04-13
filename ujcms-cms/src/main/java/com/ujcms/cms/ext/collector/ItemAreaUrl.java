package com.ujcms.cms.ext.collector;

import org.springframework.lang.Nullable;

/**
 * Item 区域
 *
 * @author PONY
 */
public class ItemAreaUrl {
    private String url;
    @Nullable
    private String area;

    public ItemAreaUrl() {
    }

    public ItemAreaUrl(String url, @Nullable String area) {
        this.url = url;
        this.area = area;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Nullable
    public String getArea() {
        return area;
    }

    public void setArea(@Nullable String area) {
        this.area = area;
    }
}
