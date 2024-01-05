package com.ujcms.cms.ext.domain;

import com.ujcms.cms.core.domain.Org;
import com.ujcms.cms.core.domain.User;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 绩效统计 实体类
 *
 * @author PONY
 */
public class PerformanceStat implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 总发布数
     */
    private Integer totalCount = 0;
    /**
     * 总绩效分
     */
    private Integer totalScore = 0;

    /**
     * 绩效
     */
    private Map<String, Number> performanceMap = new HashMap<>();

    /**
     * 用户ID
     */
    @Nullable
    private Integer userId;
    /**
     * 组织ID
     */
    @Nullable
    private Integer orgId;

    /**
     * 用户
     */
    @Nullable
    private User user;
    /**
     * 组织
     */
    @Nullable
    private Org org;

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    @Nullable
    public Integer getUserId() {
        return userId;
    }

    public void setUserId(@Nullable Integer userId) {
        this.userId = userId;
    }


    @Nullable
    public Integer getOrgId() {
        return orgId;
    }

    public void setOrgId(@Nullable Integer orgId) {
        this.orgId = orgId;
    }

    public Map<String, Number> getPerformanceMap() {
        return performanceMap;
    }

    public void setPerformanceMap(Map<String, Number> performanceMap) {
        this.performanceMap = performanceMap;
    }

    @Nullable
    public User getUser() {
        return user;
    }

    public void setUser(@Nullable User user) {
        this.user = user;
    }

    @Nullable
    public Org getOrg() {
        return org;
    }

    public void setOrg(@Nullable Org org) {
        this.org = org;
    }
}
