package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.ChannelExtBase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 栏目扩展数据实体类
 *
 * @author PONY
 */
@Schema(name = "Channel.ChannelExt")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class ChannelExt extends ChannelExtBase implements Serializable {
    private static final long serialVersionUID = 1L;
}