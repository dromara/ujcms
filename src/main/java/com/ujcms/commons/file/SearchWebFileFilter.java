package com.ujcms.commons.file;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

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
                || Strings.CS.contains(file.getName(), search);
    }
}
