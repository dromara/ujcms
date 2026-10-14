package com.ujcms.cms.core.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.ModelUsage;
import com.ujcms.cms.core.domain.generated.GeneratedModel;
import com.ujcms.cms.core.listener.ModelDeleteListener;
import com.ujcms.cms.core.mapper.ModelMapper;
import com.ujcms.cms.core.service.args.ModelArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.web.exception.LogicException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.jspecify.annotations.Nullable;
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

    /**
     * 只写系统字段集（model-management M2）：名称、共享范围、自定义字段等其它列保持库中现值
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSystemFields(Model bean, @Nullable String mains, @Nullable String asides) {
        bean.setMains(mains);
        bean.setAsides(asides);
        mapper.update(bean);
    }

    /**
     * 只写自定义字段集（model-management M2）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCustomFields(Model bean, @Nullable String customs) {
        bean.setCustoms(customs);
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

    /**
     * 删除模型。系统模型（全局设置模型 ID 1、用户模型 ID 2）按固定 ID 取用，不可删除；
     * 仍被栏目、专题、区块、表单类型、站点绑定的模型由各自的监听器拒绝
     */
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        if (Model.SYSTEM_MODEL_IDS.contains(id)) {
            throw new LogicException("error.model.sys");
        }
        deleteListeners.forEach(it -> it.preModelDelete(id));
        return mapper.delete(id);
    }

    /**
     * 批量删除：一个事务，任一行被拒绝则全部不删
     */
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
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedModel.TABLE_NAME, "scope_desc,order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<Model> selectList(ModelArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    /**
     * 模型使用统计，按模型ID索引。未被引用的模型不在其中（调用方按需补零）
     *
     * @param siteId 当前站点ID
     */
    public Map<Long, ModelUsage> listUsages(Long siteId) {
        Map<Long, ModelUsage> map = new HashMap<>(16);
        for (ModelUsage usage : mapper.selectUsages(siteId)) {
            map.put(usage.modelId(), usage);
        }
        return map;
    }

    /**
     * 模型的使用统计；未被引用则为零
     */
    public ModelUsage selectUsage(Long modelId, Long siteId) {
        return listUsages(siteId).getOrDefault(modelId, ModelUsage.none(modelId));
    }

    /**
     * 同类型、同可见集内是否已有同名模型（model-management M7）：本站私有模型看本站私有 + 全局；
     * 全局模型看全部（它对每个站点都可见）
     *
     * @param excludeId 排除的模型ID（待保存的模型自身），可为 {@code null}
     */
    public boolean existsByName(String name, String type, boolean global, Long siteId, @Nullable Long excludeId) {
        ModelArgs args = ModelArgs.of().type(type);
        if (!global) {
            args.scopeSiteId(siteId);
        }
        return selectList(args).stream()
                .anyMatch(it -> it.getName().equals(name) && !it.getId().equals(excludeId));
    }

    private List<ModelDeleteListener> deleteListeners = Collections.emptyList();

    @Lazy
    @Autowired(required = false)
    public void setDeleteListeners(List<ModelDeleteListener> deleteListeners) {
        this.deleteListeners = deleteListeners;
    }
}
