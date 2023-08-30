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
    private Integer id = 0;
    /**
     * 名称
     */
    private String name = "";
    /**
     * 数量
     */
    private Integer count = 0;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
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
