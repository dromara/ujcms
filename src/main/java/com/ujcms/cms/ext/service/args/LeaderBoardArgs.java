package com.ujcms.cms.ext.service.args;

import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.Collection;

/**
 * Example 查询参数
 *
 * @author Generator
 */
public class LeaderBoardArgs {
    @Nullable
    private Long siteId;
    @Nullable
    private Collection<Short> status;
    @Nullable
    private OffsetDateTime begin;
    @Nullable
    private OffsetDateTime end;

    @Nullable
    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(@Nullable Long siteId) {
        this.siteId = siteId;
    }

    @Nullable
    public Collection<Short> getStatus() {
        return status;
    }

    public void setStatus(@Nullable Collection<Short> status) {
        this.status = status;
    }

    @Nullable
    public OffsetDateTime getBegin() {
        return begin;
    }

    public void setBegin(@Nullable OffsetDateTime begin) {
        this.begin = begin;
    }

    @Nullable
    public OffsetDateTime getEnd() {
        return end;
    }

    public void setEnd(@Nullable OffsetDateTime end) {
        this.end = end;
    }

    public static LeaderBoardArgs of() {
        return new LeaderBoardArgs();
    }
}