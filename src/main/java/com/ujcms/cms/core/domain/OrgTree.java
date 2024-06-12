package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.OrgTreeBase;
import com.ujcms.commons.db.tree.TreeRelation;

import java.io.Serializable;

/**
 * 组织树形结构实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class OrgTree extends OrgTreeBase implements TreeRelation, Serializable {
    private static final long serialVersionUID = 1L;

    public OrgTree(Long ancestorId, Long descendantId) {
        setAncestorId(ancestorId);
        setDescendantId(descendantId);
    }
}