package com.ujcms.cms.ext.domain;

import static com.ujcms.cms.core.component.ViewCountService.BATCH_SIZE;

import com.ujcms.cms.ext.domain.generated.GeneratedVisitLog;

/**
 * 访问日志 实体类
 *
 * @author PONY
 */
public class VisitLog extends GeneratedVisitLog {

    /**
     * FOREACH条数
     * <p>
     * SqlServer 参数限制为2100个，Oracle、PostgreSQL参数限制为32767个，MySQL参数限制为65535个。
     * <p>
     * VisitLog表有14个字段，120条记录为1680个参数。最多可支持17（17*120=2040）个字段。
     */
    public static final int FOREACH_SIZE = 120;
    /**
     * 缓存条数
     */
    public static final int CACHE_SIZE = FOREACH_SIZE * BATCH_SIZE;
    /**
     * 数据最多保留天数
     */
    public static final int DATA_MAX_DAY = 3;
}