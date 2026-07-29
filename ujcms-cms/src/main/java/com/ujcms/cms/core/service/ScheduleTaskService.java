package com.ujcms.cms.core.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.ScheduleTask;
import com.ujcms.cms.core.domain.generated.GeneratedScheduleTask;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.mapper.ScheduleTaskMapper;
import com.ujcms.cms.core.service.args.ScheduleTaskArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.db.order.OrderEntityUtils;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;

/**
 * 定时任务 Service
 *
 * @author PONY
 */
@Service
public class ScheduleTaskService implements UserDeleteListener, SiteDeleteListener {
    private final ScheduleTaskMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public ScheduleTaskService(ScheduleTaskMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(ScheduleTask bean) {
        bean.setId(snowflakeSequence.nextId());
        bean.setOrder(bean.getId());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ScheduleTask bean) {
        mapper.update(bean);
    }

    /**
     * 更新执行结果。只更新执行结果相关字段，避免覆盖用户对任务配置的并发修改
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRunResult(Long id, OffsetDateTime lastRunAt, Short lastStatus, @Nullable String errorInfo) {
        mapper.updateRunResult(id, lastRunAt, lastStatus, errorInfo);
    }

    @Transactional(rollbackFor = Exception.class)
    public void moveOrder(Long fromId, Long toId) {
        OrderEntityUtils.move(mapper, fromId, toId);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public ScheduleTask select(Long id) {
        return mapper.select(id);
    }

    public List<ScheduleTask> selectList(ScheduleTaskArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedScheduleTask.TABLE_NAME, "order_,id_");
        return mapper.selectAll(queryInfo);
    }

    public List<ScheduleTask> selectList(ScheduleTaskArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    /**
     * 获取所有已启用的定时任务。用于应用启动时重建调度
     */
    public List<ScheduleTask> listEnabled() {
        return mapper.listEnabled();
    }

    @Override
    public void preUserDelete(Long userId) {
        mapper.deleteByUserId(userId);
    }

    @Override
    public void preSiteDelete(Long siteId) {
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }
}
