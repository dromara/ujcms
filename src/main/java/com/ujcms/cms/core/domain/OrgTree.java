package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.generated.GeneratedOrgTree;
import com.ujcms.commons.db.tree.TreeRelation;

/**
 * 组织树形结构实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class OrgTree extends GeneratedOrgTree implements TreeRelation {

    public OrgTree(Long ancestorId, Long descendantId) {
        setAncestorId(ancestorId);
        setDescendantId(descendantId);
    }
}