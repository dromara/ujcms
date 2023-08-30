package com.ujcms.cms.ext.domain.global;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.ujcms.cms.core.domain.global.GlobalData;
import com.ujcms.cms.core.support.Constants;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * @author PONY
 */
public class GlobalVisitorCount implements GlobalData, Serializable {
    private static final long serialVersionUID = -4925467488764239204L;

    public static final String NAME = "sys_visitor_count";

    private int visitor = 0;

    public GlobalVisitorCount() {
    }

    public GlobalVisitorCount(int visitor) {
        this.visitor = visitor;
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

    public int getVisitor() {
        return visitor;
    }

    public void setVisitor(int visitor) {
        this.visitor = visitor;
    }
}
