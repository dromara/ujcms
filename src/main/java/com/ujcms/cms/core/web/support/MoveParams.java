package com.ujcms.cms.core.web.support;

import com.ujcms.commons.db.tree.TreeMoveType;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 移动参数
 *
 * @author PONY
 */
public class MoveParams implements Serializable {
    private static final long serialVersionUID = 1;

    @NotNull
    private Long fromId = -1L;
    @NotNull
    private Long toId = -1L;
    @NotNull
    private TreeMoveType type = TreeMoveType.INNER;

    public Long getFromId() {
        return fromId;
    }

    public void setFromId(Long fromId) {
        this.fromId = fromId;
    }

    public Long getToId() {
        return toId;
    }

    public void setToId(Long toId) {
        this.toId = toId;
    }

    public TreeMoveType getType() {
        return type;
    }

    public void setType(TreeMoveType type) {
        this.type = type;
    }
}