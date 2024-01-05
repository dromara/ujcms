package com.ujcms.cms.core.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.PerformanceType;
import com.ujcms.cms.core.domain.base.PerformanceTypeBase;
import com.ujcms.cms.core.mapper.ChannelMapper;
import com.ujcms.cms.core.mapper.PerformanceTypeMapper;
import com.ujcms.cms.core.service.args.PerformanceTypeArgs;
import com.ujcms.commons.db.order.OrderEntityUtils;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;

import java.util.List;
import java.util.Objects;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 绩效类型 Service
 *
 * @author PONY
 */
@Service
public class PerformanceTypeService {
    private final ChannelMapper channelMapper;
    private final PerformanceTypeMapper mapper;

    private final SeqService seqService;

    public PerformanceTypeService(ChannelMapper channelMapper, PerformanceTypeMapper mapper, SeqService seqService) {
        this.channelMapper = channelMapper;
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(PerformanceType bean) {
        bean.setId(seqService.getNextVal(PerformanceTypeBase.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(PerformanceType bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void moveOrder(Integer fromId, Integer toId) {
        OrderEntityUtils.move(mapper, fromId, toId);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        // 将栏目中引用该绩效类型的字段设置为null
        channelMapper.updatePerformanceTypeIdToNull(id);
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public PerformanceType select(Integer id) {
        return mapper.select(id);
    }

    public List<PerformanceType> selectList(PerformanceTypeArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), PerformanceTypeBase.TABLE_NAME, "order_,id_");
        return mapper.selectAll(queryInfo);
    }

    public List<PerformanceType> selectList(PerformanceTypeArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public List<PerformanceType> listBySiteId(Integer siteId) {
        return selectList(PerformanceTypeArgs.of().siteId(siteId));
    }
}