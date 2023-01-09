package com.ujcms.cms.core.service;

import com.github.pagehelper.PageHelper;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.listener.ModelDeleteListener;
import com.ujcms.cms.core.mapper.ModelMapper;
import com.ujcms.cms.core.service.args.ModelArgs;
import com.ujcms.util.query.QueryInfo;
import com.ujcms.util.query.QueryParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 模型 Service
 *
 * @author PONY
 */
@Service
public class ModelService {
    private final ModelMapper mapper;
    private final SeqService seqService;

    public ModelService(ModelMapper mapper, SeqService seqService) {
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Model bean, @Nullable Integer siteId) {
        bean.setId(seqService.getNextVal(Model.TABLE_NAME));
        // 全局共享数据的站点ID设置为null
        bean.setSiteId(bean.isGlobal() ? null : siteId);
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Model bean, Integer siteId) {
        // 全局共享数据的站点ID设置为null
        bean.setSiteId(bean.isGlobal() ? null : siteId);
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<Model> list) {
        short order = 1;
        for (Model bean : list) {
            bean.setOrder(order);
            mapper.update(bean);
            order += 1;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        deleteListeners.forEach(it -> it.preModelDelete(id));
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteBySiteId(Integer siteId) {
        return mapper.deleteBySiteId(siteId);
    }

    @Nullable
    public Model select(Integer id) {
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
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), Model.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<Model> selectList(ModelArgs args, int offset, int limit) {
        return PageHelper.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    private List<ModelDeleteListener> deleteListeners = Collections.emptyList();

    @Lazy
    @Autowired(required = false)
    public void setDeleteListeners(List<ModelDeleteListener> deleteListeners) {
        this.deleteListeners = deleteListeners;
    }
}