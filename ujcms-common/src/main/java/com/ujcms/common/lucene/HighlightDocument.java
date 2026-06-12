package com.ujcms.common.lucene;

import org.springframework.lang.Nullable;

/**
 * Lucene 高亮文档接口
 *
 * @author PONY
 */
public interface HighlightDocument {
    String FIELD_TITLE = "title";
    String FIELD_BODY = "body";

    String getTitle();

    @Nullable
    String getBody();

    void setHighlightTitle(@Nullable String highlightTitle);

    void setHighlightBody(@Nullable String highlightBody);
}
