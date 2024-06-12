package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.ChannelTreeBase;
import com.ujcms.commons.db.tree.TreeRelation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 栏目树形结构实体类
 *
 * @author PONY
 */
@Schema(name = "Channel.ChannelTree")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class ChannelTree extends ChannelTreeBase implements TreeRelation, Serializable {
    private static final long serialVersionUID = 1L;

    public ChannelTree(Long ancestorId, Long descendantId) {
        setAncestorId(ancestorId);
        setDescendantId(descendantId);
    }
}