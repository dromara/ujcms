package com.ujcms.cms.core.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.generated.GeneratedDictType;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.DictMapper;
import com.ujcms.cms.core.mapper.DictTypeMapper;
import com.ujcms.cms.core.service.args.DictTypeArgs;
import com.ujcms.cms.core.service.args.ModelArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.web.exception.LogicException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 字典类型 Service
 *
 * @author PONY
 */
@Service
public class DictTypeService implements SiteDeleteListener {
    private final DictTypeMapper mapper;
    private final DictMapper dictMapper;
    private final ModelService modelService;
    private final SnowflakeSequence snowflakeSequence;

    public DictTypeService(DictTypeMapper mapper, DictMapper dictMapper, ModelService modelService,
                           SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.dictMapper = dictMapper;
        this.modelService = modelService;
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

    /**
     * 删除类型。系统类型（{@code sys}）不可删；仍被模型字段绑定的类型不可删（删除后该字段的选项即为空）；
     * 类型与其条目是一个聚合，条目随类型级联删除。
     */
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        DictType bean = mapper.select(id);
        if (bean == null) {
            return 0;
        }
        if (Boolean.TRUE.equals(bean.getSys())) {
            throw new LogicException("error.dictType.sys");
        }
        List<Binding> bindings = listBindings(id);
        if (!bindings.isEmpty()) {
            throw new LogicException("error.dictType.inUse", bindings.stream()
                    .map(binding -> binding.model().getName() + " - " + binding.field().getName())
                    .collect(Collectors.joining(", ")));
        }
        dictMapper.deleteByTypeId(id);
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


    /**
     * 类型列表。全局类型在前、本站类型在后，组内按 order：排序按共享范围分组维护（控制台每次只提交一组），
     * 两组的 order 各自从 1 计，混排无意义。
     */
    public List<DictType> selectList(DictTypeArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedDictType.TABLE_NAME, "scope_desc,order,id");
        return mapper.selectAll(queryInfo, args.isQueryEntryCount());
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

    /**
     * 模型自定义字段对字典类型的一处绑定
     */
    public record Binding(Model model, Model.Field field) {
    }

    /**
     * 绑定该类型的全部模型自定义字段。绑定存于模型的 customs JSON 内，无列可查，只能遍历；须遍历<b>所有</b>模型
     * 而非本站可见模型：其它站点的模型同样可以绑定全局类型。
     */
    public List<Binding> listBindings(Long typeId) {
        List<Binding> bindings = new ArrayList<>();
        for (Model model : modelService.selectList(ModelArgs.of())) {
            for (Model.Field field : model.getFieldList()) {
                if (typeId.equals(field.getDictTypeId())) {
                    bindings.add(new Binding(model, field));
                }
            }
        }
        return bindings;
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