package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.OrgTreeBase;
import com.ujcms.util.db.tree.TreeRelation;

import java.io.Serializable;

/**
 * 组织树形结构 实体类
 *
 * @author PONY
 */
public class OrgTree extends OrgTreeBase implements TreeRelation, Serializable {
    public OrgTree(Integer ancestorId, Integer descendantId) {
        setAncestorId(ancestorId);
        setDescendantId(descendantId);
    }
}