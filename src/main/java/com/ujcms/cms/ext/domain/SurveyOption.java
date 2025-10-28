package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ujcms.cms.ext.domain.base.SurveyOptionBase;

/**
 * 调查问卷选项 实体类
 *
 * @author PONY
 */
public class SurveyOption extends SurveyOptionBase {

    public double getPercent() {
        int total = getItem().getTotal();
        if (total > 0) {
            return (double) getCount() * 100 / total;
        }
        return 0;
    }

    @JsonIgnore
    private SurveyItem item = new SurveyItem();

    public SurveyItem getItem() {
        return item;
    }

    public void setItem(SurveyItem item) {
        this.item = item;
    }
}