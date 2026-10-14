package com.ujcms.cms.core.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 某一状态的文章数（文章列表状态页签上的计数）
 *
 * @author PONY
 */
public class ArticleStatusCount implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 状态
     */
    private short status;
    /**
     * 文章数
     */
    private int total;

    public short getStatus() {
        return status;
    }

    public void setStatus(short status) {
        this.status = status;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }
}
