package com.ujcms.cms.ext.domain;

import com.ujcms.cms.ext.domain.base.SurveyItemBase;
import com.ujcms.cms.ext.domain.base.SurveyOptionBase;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 调查问卷条目 实体类
 *
 * @author PONY
 */
public class SurveyItem extends SurveyItemBase implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 总票数
     */
    public int getTotal() {
        return getOptions().stream().mapToInt(SurveyOptionBase::getCount).sum();
    }

    private List<SurveyOption> options = new ArrayList<>();

    public List<SurveyOption> getOptions() {
        return options;
    }

    public void setOptions(List<SurveyOption> options) {
        this.options = options;
    }
}