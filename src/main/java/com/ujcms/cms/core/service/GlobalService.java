package com.ujcms.cms.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ujcms.cms.core.domain.Global;
import com.ujcms.cms.core.domain.global.GlobalData;
import com.ujcms.cms.core.mapper.GlobalMapper;
import com.ujcms.cms.core.support.Constants;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 全局数据 Service
 *
 * @author PONY
 */
@Service
public class GlobalService {
    private final GlobalMapper mapper;

    public GlobalService(GlobalMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Global bean) {
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Global bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(String name) {
        return mapper.delete(name);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<String> names) {
        return names.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Global select(String name) {
        return mapper.select(name);
    }

    @Transactional(rollbackFor = Exception.class)
    public <T extends GlobalData> T selectGlobalDataOrInit(T data, Class<T> clazz) {
        Global bean = select(data.getName());
        if (bean == null) {
            insert(new Global(data.getName(), data.getValue()));
            return data;
        }
        try {
            return Constants.MAPPER.readValue(bean.getValue(), clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateGlobalData(GlobalData data) {
        update(new Global(data.getName(), data.getValue()));
    }
}