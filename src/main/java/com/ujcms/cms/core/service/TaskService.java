package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ujcms.cms.core.domain.Task;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.mapper.TaskMapper;
import com.ujcms.cms.core.service.args.TaskArgs;
import com.ujcms.util.query.QueryInfo;
import com.ujcms.util.query.QueryParser;
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
    private final SeqService seqService;

    public TaskService(TaskMapper mapper, SeqService seqService) {
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Task bean) {
        bean.setId(seqService.getNextVal(Task.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Task bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Task select(Integer id) {
        return mapper.select(id);
    }

    public List<Task> selectList(TaskArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), Task.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<Task> selectList(TaskArgs args, int offset, int limit) {
        return PageHelper.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Task> selectPage(TaskArgs args, int page, int pageSize) {
        return PageHelper.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    @Override
    public void preUserDelete(Integer userId) {
        mapper.deleteByUserId(userId);
    }

    @Override
    public void preSiteDelete(Integer siteId) {
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }
}