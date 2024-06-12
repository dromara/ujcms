package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.DictBase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 字典实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Dict extends DictBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String NOT_FOUND = "Dict not found. ID: ";
}