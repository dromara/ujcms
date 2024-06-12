package com.ujcms.commons.db.order;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 移动排序Controller参数
 *
 * @author PONY
 */
public class MoveOrderParams implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull
    private Long fromId;
    @NotNull
    private Long toId;

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
}
