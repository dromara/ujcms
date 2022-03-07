package com.ujcms.core.domain;

import com.ujcms.core.domain.base.OrgTreeBase;

import java.io.Serializable;

/**
 * 组织树形结构 实体类
 *
 * @author PONY
 */
public class OrgTree extends OrgTreeBase implements Serializable {
    public OrgTree(Integer ancestorId, Integer descendantId) {
        setAncestorId(ancestorId);
        setDescendantId(descendantId);
    }
}