package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.PerformanceTypeBase;
import com.ujcms.commons.db.order.OrderEntity;

import java.io.Serializable;

/**
 * @author PONY
 */
public class PerformanceType extends PerformanceTypeBase implements Serializable, OrderEntity {
    private static final long serialVersionUID = 1L;

    public static final String NOT_FOUND = "PerformanceType not found. ID: ";
}