package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.Action;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.service.ActionService;
import com.ujcms.cms.ext.domain.Vote;
import com.ujcms.cms.ext.domain.VoteOption;
import com.ujcms.cms.ext.domain.base.VoteBase;
import com.ujcms.cms.ext.domain.base.VoteOptionBase;
import com.ujcms.cms.ext.mapper.VoteMapper;
import com.ujcms.cms.ext.mapper.VoteOptionMapper;
import com.ujcms.cms.ext.service.args.VoteArgs;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.db.order.OrderEntityUtils;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 投票 Service
 *
 * @author PONY
 */
@Service
public class VoteService implements SiteDeleteListener {
    private final ActionService actionService;
    private final VoteMapper mapper;
    private final VoteOptionMapper optionMapper;
    private final SnowflakeSequence snowflakeSequence;

    public VoteService(ActionService actionService, VoteMapper mapper, VoteOptionMapper optionMapper,
                       SnowflakeSequence snowflakeSequence) {
        this.actionService = actionService;
        this.mapper = mapper;
        this.optionMapper = optionMapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    private void updateVoteOptions(Long voteId, List<VoteOption> optionList) {
        // 删除
        optionMapper.deleteByVoteId(voteId,
                optionList.stream().map(VoteOptionBase::getId).filter(id -> id > 0).collect(Collectors.toList()));
        int order = 1;
        for (VoteOption option : optionList) {
            option.setVoteId(voteId);
            option.setOrder(order);
            order += 1;
            if (option.getId() > 0) {
                // 更新
                optionMapper.update(option);
            } else {
                // 新增
                option.setId(snowflakeSequence.nextId());
                optionMapper.insert(option);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Vote bean, List<VoteOption> options) {
        bean.setId(snowflakeSequence.nextId());
        bean.setOrder(bean.getId());
        mapper.insert(bean);
        updateVoteOptions(bean.getId(), options);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Vote bean, List<VoteOption> options) {
        mapper.update(bean);
        updateVoteOptions(bean.getId(), options);
    }

    @Transactional(rollbackFor = Exception.class)
    public void moveOrder(Long fromId, Long toId) {
        OrderEntityUtils.move(mapper, fromId, toId);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        optionMapper.deleteByVoteId(id, Collections.emptyList());
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Vote select(Long id) {
        return mapper.select(id);
    }

    public List<Vote> selectList(VoteArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), VoteBase.TABLE_NAME, "order_desc,id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<Vote> selectList(VoteArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Vote> selectPage(VoteArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    @Transactional(rollbackFor = Exception.class)
    public void cast(Long id, List<Long> optionIds,
                     Long siteId, @Nullable Long userId, String ip, long cookie) {
        String refOption = optionIds.stream().map(Object::toString).collect(Collectors.joining(","));
        actionService.insert(new Action(Vote.ACTION_TYPE, id, refOption, siteId, userId, ip, cookie));
        mapper.cast(id);
        optionMapper.cast(id, optionIds);
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }

    @Override
    public void preSiteDelete(Long siteId) {
        optionMapper.deleteBySiteId(siteId);
        mapper.deleteBySiteId(siteId);
    }
}