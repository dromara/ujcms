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
    private Integer fromId;
    @NotNull
    private Integer toId;

    public Integer getFromId() {
        return fromId;
    }

    public void setFromId(Integer fromId) {
        this.fromId = fromId;
    }

    public Integer getToId() {
        return toId;
    }

    public void setToId(Integer toId) {
        this.toId = toId;
    }
}
