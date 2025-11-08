package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.generated.GeneratedDict;

/**
 * 字典实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Dict extends GeneratedDict {

    public static final String NOT_FOUND = "Dict not found. ID: ";
}