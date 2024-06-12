package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 站点缓冲实体类
 *
 * @author PONY
 */
@Schema(name = "Site.SiteBuffer")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class SiteBuffer implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 站点ID
     */
    @NotNull
    @Schema(description="站点ID")
    private Long id = 0L;

    /**
     * 浏览次数
     */
    @NotNull
    @Schema(description="浏览次数")
    private Long views = 0L;

    /**
     * 首页浏览次数
     */
    @NotNull
    @Schema(description="首页浏览次数")
    private Long selfViews = 0L;

    /**
     * 今日浏览次数
     */
    @NotNull
    @Schema(description="今日浏览次数")
    private Integer todayViews = 0;

    /**
     * 昨日浏览次数
     */
    @NotNull
    @Schema(description="昨日浏览次数")
    private Integer yesterdayViews = 0;

    /**
     * 最高浏览次数
     */
    @NotNull
    @Schema(description="最高浏览次数")
    private Integer maxViews = 0;

    /**
     * 最高浏览日期
     */
    @NotNull
    @Schema(description="最高浏览日期")
    private OffsetDateTime maxDate = OffsetDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getViews() {
        return views;
    }

    public void setViews(Long views) {
        this.views = views;
    }

    public Long getSelfViews() {
        return selfViews;
    }

    public void setSelfViews(Long selfViews) {
        this.selfViews = selfViews;
    }

    public Integer getTodayViews() {
        return todayViews;
    }

    public void setTodayViews(Integer todayViews) {
        this.todayViews = todayViews;
    }

    public Integer getYesterdayViews() {
        return yesterdayViews;
    }

    public void setYesterdayViews(Integer yesterdayViews) {
        this.yesterdayViews = yesterdayViews;
    }

    public Integer getMaxViews() {
        return maxViews;
    }

    public void setMaxViews(Integer maxViews) {
        this.maxViews = maxViews;
    }

    public OffsetDateTime getMaxDate() {
        return maxDate;
    }

    public void setMaxDate(OffsetDateTime maxDate) {
        this.maxDate = maxDate;
    }
}