package com.ujcms.core.domain;

import com.ujcms.core.domain.base.SiteBufferBase;

import java.io.Serializable;

/**
 * 站点缓冲 实体类
 *
 * @author PONY
 */
public class SiteBuffer extends SiteBufferBase implements Serializable {
    public SiteBuffer() {
    }

    public SiteBuffer(Integer id) {
        setId(id);
    }
}