package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.SiteBufferBase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 站点缓冲实体类
 *
 * @author PONY
 */
@Schema(name = "Site.SiteBuffer")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class SiteBuffer extends SiteBufferBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public SiteBuffer() {
    }

    public SiteBuffer(Integer id) {
        setId(id);
    }
}