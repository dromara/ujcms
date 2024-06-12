package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.OperationLog;
import com.ujcms.cms.core.domain.OperationLogExt;
import com.ujcms.cms.core.domain.base.OperationLogBase;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.mapper.OperationLogExtMapper;
import com.ujcms.cms.core.mapper.OperationLogMapper;
import com.ujcms.cms.core.service.args.OperationLogArgs;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 操作日志 Service
 *
 * @author PONY
 */
@Service
public class OperationLogService implements SiteDeleteListener, UserDeleteListener {
    private final OperationLogExtMapper extMapper;
    private final OperationLogMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public OperationLogService(OperationLogExtMapper extMapper, OperationLogMapper mapper,
                               SnowflakeSequence snowflakeSequence) {
        this.extMapper = extMapper;
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Async
    @Transactional(rollbackFor = Exception.class)
    public void asyncInsert(OperationLog bean, OperationLogExt ext) {
        bean.setId(snowflakeSequence.nextId());
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
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), OperationLogBase.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<OperationLog> selectList(OperationLogArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<OperationLog> selectPage(OperationLogArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    @Override
    public void preSiteDelete(Long siteId) {
        extMapper.deleteBySiteId(siteId);
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }

    @Override
    public void preUserDelete(Long userId) {
        extMapper.deleteByUserId(userId);
        mapper.deleteByUserId(userId);
    }
}