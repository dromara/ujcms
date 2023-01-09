package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ujcms.cms.core.domain.OperationLog;
import com.ujcms.cms.core.domain.OperationLogExt;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.OperationLogExtMapper;
import com.ujcms.cms.core.mapper.OperationLogMapper;
import com.ujcms.cms.core.service.args.OperationLogArgs;
import com.ujcms.util.query.QueryInfo;
import com.ujcms.util.query.QueryParser;

import java.util.List;
import java.util.Objects;

import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 操作日志 Service
 *
 * @author PONY
 */
@Service
public class OperationLogService implements SiteDeleteListener {
    private final OperationLogExtMapper extMapper;
    private final OperationLogMapper mapper;
    private final SeqService seqService;

    public OperationLogService(OperationLogExtMapper extMapper, OperationLogMapper mapper, SeqService seqService) {
        this.extMapper = extMapper;
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Async
    @Transactional(rollbackFor = Exception.class)
    public void asyncInsert(OperationLog bean, OperationLogExt ext) {
        bean.setId(seqService.getNextValLong(OperationLog.TABLE_NAME));
        mapper.insert(bean);
        ext.setId(bean.getId());
        extMapper.insert(ext);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        extMapper.delete(id);
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public OperationLog select(Long id) {
        return mapper.select(id);
    }

    public List<OperationLog> selectList(OperationLogArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), OperationLog.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<OperationLog> selectList(OperationLogArgs args, int offset, int limit) {
        return PageHelper.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<OperationLog> selectPage(OperationLogArgs args, int page, int pageSize) {
        return PageHelper.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    @Override
    public void preSiteDelete(Integer siteId) {
        extMapper.deleteBySiteId(siteId);
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }
}