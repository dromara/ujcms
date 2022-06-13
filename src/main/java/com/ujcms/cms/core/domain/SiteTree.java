package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.SiteTreeBase;
import com.ujcms.util.db.tree.TreeRelation;

import java.io.Serializable;

/**
 * 站点树形结构 实体类
 *
 * @author PONY
 */
public class SiteTree extends SiteTreeBase implements TreeRelation, Serializable {
    public SiteTree(Integer ancestorId, Integer descendantId) {
        setAncestorId(ancestorId);
        setDescendantId(descendantId);
    }
}