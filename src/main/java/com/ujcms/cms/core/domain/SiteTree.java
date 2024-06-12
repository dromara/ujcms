package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.SiteTreeBase;
import com.ujcms.commons.db.tree.TreeRelation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 站点树形结构实体类
 *
 * @author PONY
 */
@Schema(name = "Site.SiteTree")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class SiteTree extends SiteTreeBase implements TreeRelation, Serializable {
    private static final long serialVersionUID = 1L;

    public SiteTree(Long ancestorId, Long descendantId) {
        setAncestorId(ancestorId);
        setDescendantId(descendantId);
    }
}