package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.ChannelCustomBase;
import com.ujcms.cms.core.domain.support.CustomBean;

import java.io.Serializable;

/**
 * 栏目自定义数据 实体类
 *
 * @author PONY
 */
public class ChannelCustom extends ChannelCustomBase implements Serializable, CustomBean {
    public ChannelCustom() {
    }

    public ChannelCustom(Integer channelId, String name, String type, String value) {
        setChannelId(channelId);
        setName(name);
        setType(type);
        setValue(value);
    }
}