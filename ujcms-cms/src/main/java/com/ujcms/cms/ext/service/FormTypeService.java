package com.ujcms.cms.ext.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.listener.ModelDeleteListener;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.service.ModelService;
import com.ujcms.cms.core.service.ProcessDefinitionPort;
import com.ujcms.cms.ext.domain.Form;
import com.ujcms.cms.ext.domain.FormType;
import com.ujcms.cms.ext.domain.generated.GeneratedFormType;
import com.ujcms.cms.ext.mapper.FormMapper;
import com.ujcms.cms.ext.mapper.FormTypeMapper;
import com.ujcms.cms.ext.service.args.FormTypeArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.db.order.OrderEntityUtils;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.LogicException;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 表单类型 Service（核心）：读取与写入路径都在核心——自定义表单的功能面（后台管理、前台页面、提交、标签）
 * 属于 T2，但表单类型定义与所有版本共用一套 schema，主题演示数据在社区版/基础版同样完整落库，
 * 升级后无需补导（theme-seed-export D46）。
 *
 * @author PONY
 */
@Service
public class FormTypeService implements SiteDeleteListener, ModelDeleteListener {
    private final FormMapper formMapper;
    private final FormTypeMapper mapper;
    private final ModelService modelService;
    private final SnowflakeSequence snowflakeSequence;
    private final ObjectProvider<ProcessDefinitionPort> processDefinitionPort;

    public FormTypeService(FormMapper formMapper, FormTypeMapper mapper, ModelService modelService,
                           SnowflakeSequence snowflakeSequence,
                           ObjectProvider<ProcessDefinitionPort> processDefinitionPort) {
        this.formMapper = formMapper;
        this.mapper = mapper;
        this.modelService = modelService;
        this.snowflakeSequence = snowflakeSequence;
        this.processDefinitionPort = processDefinitionPort;
    }

    /**
     * 校验表单类型的审核流程（docs/spec/article-data-permission/plan.md D23）：留空即无流程；否则须是本站已部署的表单流程。
     * 与栏目同一规则（{@link com.ujcms.cms.core.service.ChannelService#validateProcessKey}），只在控制台新增、
     * 改换流程键时调用
     */
    public void validateProcessKey(Long siteId, @Nullable String processKey) {
        if (StringUtils.isBlank(processKey)) {
            return;
        }
        ProcessDefinitionPort port = processDefinitionPort.getIfAvailable();
        if (port == null || !port.isDeployed(siteId, Form.PROCESS_CATEGORY, processKey)) {
            throw new Http400Exception("error.formType.processInvalid", processKey);
        }
    }

    /**
     * 校验表单绑定的模型（docs/spec/article-data-permission/plan.md D16）：存在、是表单模型、属于本站或全局共享。
     * 与栏目模型同一规则（{@link com.ujcms.cms.core.service.ChannelService#validateModels}），只在控制台新增、
     * 改换模型时调用——种子导入按名称在本站内解析模型，另有保证，不经此处。
     */
    public void validateModel(Long siteId, Long modelId) {
        Model model = modelService.select(modelId);
        if (model == null || !FormType.MODEL_TYPE.equals(model.getType())
                || !(model.isGlobal() || siteId.equals(model.getSiteId()))) {
            throw new Http400Exception("error.formType.modelInvalid", String.valueOf(modelId));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(FormType bean) {
        bean.setId(snowflakeSequence.nextId());
        bean.setOrder(bean.getId());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(FormType bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void moveOrder(Long fromId, Long toId) {
        OrderEntityUtils.move(mapper, fromId, toId);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        // 表单是访客提交的数据，不随类型级联删除。提示不指向菜单路径：类型就在“表单管理”里删，用户此刻正在那一屏
        if (formMapper.existsByTypeId(id) > 0) {
            throw new LogicException("error.formType.hasForms");
        }
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public FormType select(Long id) {
        return mapper.select(id);
    }

    public List<FormType> selectList(FormTypeArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedFormType.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo, args.isQueryFormCount());
    }

    public List<FormType> selectList(FormTypeArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public boolean existsByModelId(Long modelId) {
        return mapper.existsByModelId(modelId) > 0;
    }

    @Override
    public void preModelDelete(Long modelId) {
        if (existsByModelId(modelId)) {
            throw new LogicException("error.refer.formType");
        }
    }

    @Override
    public void preSiteDelete(Long siteId) {
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }
}
