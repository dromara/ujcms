package com.ujcms.cms.ext.domain;

import java.io.Serializable;

/**
 * 排行榜
 *
 * @author PONY
 */
public class LeaderBoard implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    private Long id = 0L;
    /**
     * 名称
     */
    private String name = "";
    /**
     * 数量
     */
    private Integer count = 0;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}
