package com.ujcms.core.domain;

import com.ujcms.core.domain.base.ChannelBufferBase;

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