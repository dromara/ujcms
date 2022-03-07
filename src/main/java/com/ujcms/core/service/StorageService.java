package com.ujcms.core.service;

import com.github.pagehelper.PageHelper;
import com.ofwise.util.query.QueryInfo;
import com.ofwise.util.query.QueryParser;
import com.ujcms.core.domain.Storage;
import com.ujcms.core.mapper.StorageMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 存储 Service
 *
 * @author PONY
 */
@Service
public class StorageService {
    private StorageMapper mapper;

    private SeqService seqService;

    public StorageService(StorageMapper mapper, SeqService seqService) {
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Storage bean) {
        bean.setId(seqService.getNextVal(Storage.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Storage bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<Storage> list) {
        short order = 1;
        for (Storage bean : list) {
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
        return ids.stream().filter(Objects::nonNull).mapToInt(mapper::delete).sum();
    }

    @Nullable
    public Storage select(Integer id) {
        return mapper.select(id);
    }

    public List<Storage> selectList(@Nullable Map<String, Object> queryMap) {
        QueryInfo queryInfo = QueryParser.parse(queryMap, Storage.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<Storage> selectList(@Nullable Map<String, Object> queryMap, int offset, int limit) {
        return PageHelper.offsetPage(offset, limit, false).doSelectPage(() -> selectList(queryMap));
    }

    public List<Storage> listByMode(short mode) {
        return mapper.listByMode(mode);
    }
}