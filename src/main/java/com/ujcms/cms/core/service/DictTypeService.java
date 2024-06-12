package com.ujcms.cms.core.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.base.DictTypeBase;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.DictMapper;
import com.ujcms.cms.core.mapper.DictTypeMapper;
import com.ujcms.cms.core.service.args.DictTypeArgs;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import com.ujcms.commons.web.exception.LogicException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 字典类型 Service
 *
 * @author PONY
 */
@Service
public class DictTypeService implements SiteDeleteListener {
    private final DictTypeMapper mapper;
    private final DictMapper dictMapper;
    private final SnowflakeSequence snowflakeSequence;

    public DictTypeService(DictTypeMapper mapper, DictMapper dictMapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.dictMapper = dictMapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(DictType bean, Long siteId) {
        bean.setId(snowflakeSequence.nextId());
        // 全局共享数据的站点ID设置为null
        bean.setSiteId(bean.isGlobal() ? null : siteId);
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(DictType bean, Long siteId) {
        // 全局共享数据的站点ID设置为null
        bean.setSiteId(bean.isGlobal() ? null : siteId);
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<DictType> list) {
        int order = 1;
        for (DictType bean : list) {
            bean.setOrder(order);
            mapper.update(bean);
            order += 1;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        if (dictMapper.existsByTypeId(id) > 0) {
            throw new LogicException("error.refer.dict");
        }
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public DictType select(Long id) {
        return mapper.select(id);
    }


    public List<DictType> selectList(DictTypeArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), DictTypeBase.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<DictType> selectList(DictTypeArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public List<DictType> listBySiteId(Long siteId) {
        return selectList(DictTypeArgs.of().siteId(siteId));
    }

    public boolean existsByAlias(String alias, @Nullable Long siteId) {
        return mapper.existsByAlias(alias, siteId) > 0;
    }

    @Override
    public void preSiteDelete(Long siteId) {
        dictMapper.deleteBySiteId(siteId);
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }
}