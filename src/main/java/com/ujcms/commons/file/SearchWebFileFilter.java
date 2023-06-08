package com.ujcms.commons.file;

import org.apache.commons.lang3.StringUtils;

/**
 * @author PONY
 */
public class SearchWebFileFilter implements WebFileFilter {
    private final String search;

    public SearchWebFileFilter(String search) {
        this.search = search;
    }

    @Override
    public boolean accept(WebFile file) {
        return StringUtils.isBlank(search)
                || StringUtils.contains(file.getName(), search);
    }
}
