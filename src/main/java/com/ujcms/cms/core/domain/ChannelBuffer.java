package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.ChannelBufferBase;

import java.io.Serializable;

/**
 * 栏目缓冲 实体类
 *
 * @author PONY
 */
public class ChannelBuffer extends ChannelBufferBase implements Serializable {
    public ChannelBuffer() {
    }

    public ChannelBuffer(Integer id) {
        setId(id);
    }
}