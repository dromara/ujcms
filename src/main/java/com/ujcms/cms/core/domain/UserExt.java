package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.UserExtBase;

import java.io.Serializable;

/**
 * 用户扩展数据实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class UserExt extends UserExtBase implements Serializable {
    private static final long serialVersionUID = 1L;
}