package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.OperationLogExtBase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 操作日志扩展实体类
 *
 * @author PONY
 */
@Schema(description = "OperationLogExt")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class OperationLogExt extends OperationLogExtBase implements Serializable {
    private static final long serialVersionUID = 1L;
}