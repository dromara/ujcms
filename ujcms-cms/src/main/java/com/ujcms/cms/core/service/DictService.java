package com.ujcms.cms.core.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.Dict;
import com.ujcms.cms.core.domain.generated.GeneratedDict;
import com.ujcms.cms.core.mapper.DictMapper;
import com.ujcms.cms.core.service.args.DictArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.web.exception.LogicException;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 字典 Service
 *
 * @author PONY
 */
@Service
public class DictService {
    private final DictMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public DictService(DictMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Dict bean) {
        bean.setId(snowflakeSequence.nextId());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Dict bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<Dict> list) {
        int order = 1;
        for (Dict bean : list) {
            bean.setOrder(order);
            mapper.update(bean);
            order += 1;
        }
    }

    /**
     * 删除条目。系统条目（{@code sys}）为系统状态，任何路径（控制台、种子）均不可删。
     */
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        Dict bean = mapper.select(id);
        if (bean == null) {
            return 0;
        }
        if (Boolean.TRUE.equals(bean.getSys())) {
            throw new LogicException("error.dict.sys");
        }
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Dict select(Long id) {
        return mapper.select(id);
    }

    public List<Dict> selectList(DictArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedDict.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<Dict> selectList(DictArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public List<Dict> listByTypeId(Long typeId) {
        return selectList(DictArgs.of().typeId(typeId));
    }

    /**
     * 按类型别名取条目，可见范围 = 本站 ∪ 全局（{@link DictArgs#scopeSiteId}）。
     */
    public List<Dict> listByAlias(String alias, @Nullable String name, Long siteId) {
        DictArgs args = DictArgs.of().typeAlias(alias).scopeSiteId(siteId);
        if (StringUtils.isNotBlank(name)) {
            args.getQueryMap().put("Contains_name", name);
        }
        return selectList(args);
    }

    /**
     * 同一类型内 value 是否已存在（条目按 value 定位——种子刷新亦以此为键）。
     */
    public boolean existsByValue(Long typeId, String value) {
        return mapper.existsByValue(typeId, value) > 0;
    }
}