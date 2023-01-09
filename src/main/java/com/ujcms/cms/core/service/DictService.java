package com.ujcms.cms.core.service;

import com.github.pagehelper.PageHelper;
import com.ujcms.cms.core.domain.Dict;
import com.ujcms.cms.core.mapper.DictMapper;
import com.ujcms.cms.core.service.args.DictArgs;
import com.ujcms.util.query.QueryInfo;
import com.ujcms.util.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_GLOBAL;

/**
 * 字典 Service
 *
 * @author PONY
 */
@Service
public class DictService {
    private final DictMapper mapper;
    private final SeqService seqService;

    public DictService(DictMapper mapper, SeqService seqService) {
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Dict bean) {
        bean.setId(seqService.getNextVal(Dict.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Dict bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<Dict> list) {
        short order = 1;
        for (Dict bean : list) {
            bean.setOrder(order);
            mapper.update(bean);
            order += 1;
        }
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
    public Dict select(Integer id) {
        return mapper.select(id);
    }

    public List<Dict> selectList(DictArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), Dict.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<Dict> selectList(DictArgs args, int offset, int limit) {
        return PageHelper.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public List<Dict> listByTypeId(Integer typeId) {
        return selectList(DictArgs.of().typeId(typeId));
    }

    public List<Dict> listByAlias(String alias, String name, Integer siteId) {
        Map<String, Object> queryMap = new HashMap<>(16);
        queryMap.put("EQ_1-1_type@dictType-alias", alias);
        queryMap.put("EQ_1-2_type@dictType-scope", SCOPE_GLOBAL);
        queryMap.put("EQ_2-1_type@dictType-alias", alias);
        queryMap.put("EQ_2-2_type@dictType-siteId", siteId);
        queryMap.put("Contains_name", name);
        return selectList(DictArgs.of(queryMap));
    }
}