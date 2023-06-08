package com.ujcms.cms.core.domain.global;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.ujcms.cms.core.support.Constants;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * @author PONY
 */
public class GlobalViewCount implements GlobalData, Serializable {
    private static final long serialVersionUID = -4925467488764239204L;

    public static final String NAME = "sys_view_count";

    private LocalDate day = LocalDate.ofYearDay(1970, 1);
    private LocalDate week = LocalDate.ofYearDay(1970, 1);
    private LocalDate month = LocalDate.ofYearDay(1970, 1);
    private LocalDate quarter = LocalDate.ofYearDay(1970, 1);
    private LocalDate year = LocalDate.ofYearDay(1970, 1);

    public GlobalViewCount() {
    }

    public GlobalViewCount(LocalDate day, LocalDate week, LocalDate month, LocalDate quarter, LocalDate year) {
        this.day = day;
        this.week = week;
        this.month = month;
        this.quarter = quarter;
        this.year = year;
    }

    @JsonIgnore
    @Override
    public String getName() {
        return NAME;
    }

    @JsonIgnore
    @Override
    public String getValue() {
        try {
            return Constants.MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public LocalDate getDay() {
        return day;
    }

    public void setDay(LocalDate day) {
        this.day = day;
    }

    public LocalDate getWeek() {
        return week;
    }

    public void setWeek(LocalDate week) {
        this.week = week;
    }

    public LocalDate getMonth() {
        return month;
    }

    public void setMonth(LocalDate month) {
        this.month = month;
    }

    public LocalDate getQuarter() {
        return quarter;
    }

    public void setQuarter(LocalDate quarter) {
        this.quarter = quarter;
    }

    public LocalDate getYear() {
        return year;
    }

    public void setYear(LocalDate year) {
        this.year = year;
    }
}
