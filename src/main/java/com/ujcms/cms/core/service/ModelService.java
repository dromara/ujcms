package com.ujcms.cms.core.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.base.ModelBase;
import com.ujcms.cms.core.listener.ModelDeleteListener;
import com.ujcms.cms.core.mapper.ModelMapper;
import com.ujcms.cms.core.service.args.ModelArgs;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 模型 Service
 *
 * @author PONY
 */
@Service
public class ModelService {
    private final ModelMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public ModelService(ModelMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Model bean, @Nullable Long siteId) {
        bean.setId(snowflakeSequence.nextId());
        // 全局共享数据的站点ID设置为null
        bean.setSiteId(bean.isGlobal() ? null : siteId);
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Model bean, Long siteId) {
        // 全局共享数据的站点ID设置为null
        bean.setSiteId(bean.isGlobal() ? null : siteId);
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<Model> list) {
        int order = 1;
        for (Model bean : list) {
            bean.setOrder(order);
            mapper.update(bean);
            order += 1;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        deleteListeners.forEach(it -> it.preModelDelete(id));
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Model select(Long id) {
        return mapper.select(id);
    }

    public Model selectConfigModel() {
        Model model = mapper.selectConfigModel();
        if (model == null) {
            throw new IllegalStateException("Model not found ID: 1 (global model)");
        }
        return model;
    }

    public Model selectUserModel() {
        Model model = mapper.selectUserModel();
        if (model == null) {
            throw new IllegalStateException("Model not found ID: 2 (user model)");
        }
        return model;
    }

    public List<Model> selectList(ModelArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), ModelBase.TABLE_NAME, "scope_desc,order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<Model> selectList(ModelArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    private List<ModelDeleteListener> deleteListeners = Collections.emptyList();

    @Lazy
    @Autowired(required = false)
    public void setDeleteListeners(List<ModelDeleteListener> deleteListeners) {
        this.deleteListeners = deleteListeners;
    }
}