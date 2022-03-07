package com.ujcms.core.domain;

import com.ujcms.core.domain.base.SiteTreeBase;

import java.io.Serializable;

/**
 * 站点树形结构 实体类
 *
 * @author PONY
 */
public class SiteTree extends SiteTreeBase implements Serializable {
    public SiteTree(Integer ancestorId, Integer descendantId) {
        setAncestorId(ancestorId);
        setDescendantId(descendantId);
    }
}