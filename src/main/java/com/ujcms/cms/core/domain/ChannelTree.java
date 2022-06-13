package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.ChannelTreeBase;
import com.ujcms.util.db.tree.TreeRelation;

import java.io.Serializable;

/**
 * 栏目树形结构 实体类
 *
 * @author PONY
 */
public class ChannelTree extends ChannelTreeBase implements TreeRelation, Serializable {
    public ChannelTree(Integer ancestorId, Integer descendantId) {
        setAncestorId(ancestorId);
        setDescendantId(descendantId);
    }
}