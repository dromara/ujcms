package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.ChannelBufferBase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 栏目缓冲实体类
 *
 * @author PONY
 */
@Schema(name = "Channel.ChannelBuffer")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class ChannelBuffer extends ChannelBufferBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public ChannelBuffer() {
    }

    public ChannelBuffer(Integer id) {
        setId(id);
    }
}