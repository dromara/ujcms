package com.ujcms.core.domain;

import com.ujcms.core.domain.base.ChannelTreeBase;

import java.io.Serializable;

/**
 * 栏目树形结构 实体类
 *
 * @author PONY
 */
public class ChannelTree extends ChannelTreeBase implements Serializable {
    public ChannelTree(Integer ancestorId, Integer descendantId) {
        setAncestorId(ancestorId);
        setDescendantId(descendantId);
    }
}