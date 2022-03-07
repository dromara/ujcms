package com.ujcms.core.domain;

import com.ujcms.core.domain.base.ChannelCustomBase;
import com.ujcms.core.domain.support.CustomBean;

import java.io.Serializable;

/**
 * 栏目自定义数据 实体类
 *
 * @author PONY
 */
public class ChannelCustom extends ChannelCustomBase implements Serializable, CustomBean {
    public ChannelCustom() {
    }

    public ChannelCustom(Integer channelId, String name, String value) {
        setChannelId(channelId);
        setName(name);
        setValue(value);
    }
}