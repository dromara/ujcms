package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 栏目缓冲实体类
 *
 * @author PONY
 */
@Schema(name = "Channel.ChannelBuffer")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class ChannelBuffer implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 栏目ID
     */
    @NotNull
    @Schema(description="栏目ID")
    private Long id = 0L;

    /**
     * 浏览次数
     */
    @NotNull
    @Schema(description="浏览次数")
    private Long views = 0L;

    /**
     * 栏目页浏览次数
     */
    @NotNull
    @Schema(description="栏目页浏览次数")
    private Long selfViews = 0L;

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
}