package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.PerformanceTypeBase;
import com.ujcms.commons.db.order.OrderEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

/**
 * @author PONY
 */
public class PerformanceType extends PerformanceTypeBase implements Serializable, OrderEntity {
    private static final long serialVersionUID = 1L;
}