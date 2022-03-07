package com.ujcms.core.service;

import com.github.pagehelper.PageHelper;
import com.ujcms.core.domain.Dict;
import com.ujcms.core.mapper.DictMapper;
import com.ofwise.util.query.QueryInfo;
import com.ofwise.util.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 字典 Service
 *
 * @author PONY
 */
@Service
public class DictService {
    private DictMapper mapper;

    private SeqService seqService;

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

    public List<Dict> selectList(@Nullable Map<String, Object> queryMap) {
        QueryInfo queryInfo = QueryParser.parse(queryMap, Dict.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<Dict> selectList(@Nullable Map<String, Object> queryMap,
                                 @Nullable Integer offset, @Nullable Integer limit) {
        return PageHelper.offsetPage(offset == null ? 0 : offset, limit == null ? Integer.MAX_VALUE : limit, false)
                .doSelectPage(() -> selectList(queryMap));
    }
}