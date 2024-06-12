package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.Action;
import com.ujcms.cms.core.domain.base.ActionBase;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.mapper.ActionMapper;
import com.ujcms.cms.core.service.args.ActionArgs;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 动作 Service
 *
 * @author PONY
 */
@Service
public class ActionService implements SiteDeleteListener, UserDeleteListener {
    private final ActionMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public ActionService(ActionMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Action bean) {
        bean.setId(snowflakeSequence.nextId());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Action bean) {
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
    public Action select(Long id) {
        return mapper.select(id);
    }

    public List<Action> selectList(ActionArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), ActionBase.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<Action> selectList(ActionArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Action> selectPage(ActionArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    public boolean existsBy(@Nullable String refType, @Nullable Long refId, @Nullable String refOption,
                            @Nullable OffsetDateTime date,
                            @Nullable Long userId, @Nullable String ip, @Nullable Long cookie) {
        return mapper.existsBy(refType, refId, refOption, date, userId, ip, cookie) > 0;
    }

    @Override
    public int deleteListenerOrder() {
        return 200;
    }

    @Override
    public void preSiteDelete(Long siteId) {
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public void preUserDelete(Long userId) {
        mapper.deleteByUserId(userId);
    }
}