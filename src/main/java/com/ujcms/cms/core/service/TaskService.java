package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.Task;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.mapper.TaskMapper;
import com.ujcms.cms.core.service.args.TaskArgs;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 任务 Service
 *
 * @author PONY
 */
@Service
public class TaskService implements UserDeleteListener, SiteDeleteListener {
    private final TaskMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public TaskService(TaskMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Task bean) {
        bean.setId(snowflakeSequence.nextId());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Task bean) {
        mapper.update(bean);
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
    public Task select(Long id) {
        return mapper.select(id);
    }

    public List<Task> selectList(TaskArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), Task.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<Task> selectList(TaskArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Task> selectPage(TaskArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
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