package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.ChannelCustomBase;
import com.ujcms.cms.core.domain.support.CustomBean;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 栏目自定义数据实体类
 *
 * @author PONY
 */
@Schema(name = "Channel.ChannelCustom")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class ChannelCustom extends ChannelCustomBase implements Serializable, CustomBean {
    private static final long serialVersionUID = 1L;

    public ChannelCustom() {
    }

    public ChannelCustom(Integer channelId, String name, String type, String value) {
        setChannelId(channelId);
        setName(name);
        setType(type);
        setValue(value);
    }
}