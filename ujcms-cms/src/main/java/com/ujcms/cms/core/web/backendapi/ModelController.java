package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.ModelUsage;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.domain.support.ModelFieldRules;
import com.ujcms.cms.core.service.DictTypeService;
import com.ujcms.cms.core.service.ModelService;
import com.ujcms.cms.core.service.args.ModelArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.common.web.Entities;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http404Exception;
import com.ujcms.common.web.exception.LogicException;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_GLOBAL;
import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_PRIVATE;
import static com.ujcms.cms.core.support.Contexts.getCurrentSiteId;
import static com.ujcms.cms.core.web.support.ValidUtils.dataInSite;
import static com.ujcms.cms.core.web.support.ValidUtils.globalPermission;

/**
 * 模型 Controller（docs/spec/model-management/plan.md）
 *
 * <p>写接口各管一段：{@code PUT /} 只写名称与共享范围，字段集走 {@code {id}/system-fields} 与
 * {@code {id}/custom-fields}（M2）——三个编辑器各自回传整条模型时，后保存的会把别人改的字段覆盖回去。
 * 类型创建后不可改，{@code order} 只经排序接口改写（master-detail R6），来源三元组由种子导入器专管。
 *
 * @author PONY
 */
@RestController("backendModelController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/model")
public class ModelController {
    private final ModelService service;
    private final DictTypeService dictTypeService;

    public ModelController(ModelService service, DictTypeService dictTypeService) {
        this.service = service;
        this.dictTypeService = dictTypeService;
    }

    /**
     * 列表只回答类型页签（M1）：不接收请求里的 {@code Q_} 条件——它们曾与站点范围条件同在一个 OR 组里，
     * 一个 {@code Q_GT_1_id_Long=0} 就能把别的站点的私有模型查出来
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('model:list','*')")
    public Object list(@Nullable String type) {
        return service.selectList(visibleArgs(type));
    }

    /**
     * 本站可见模型的使用统计（M4）：与列表同一权限、同一可见集；未被引用的模型补零。
     * 控制台据此显示“使用情况”、禁用不可删除的行、锁定不可改的共享范围
     */
    @GetMapping("usages")
    @PreAuthorize("hasAnyAuthority('model:list','*')")
    public List<UsageResult> usages(@Nullable String type) {
        Long siteId = getCurrentSiteId();
        Map<Long, ModelUsage> usages = service.listUsages(siteId);
        return service.selectList(visibleArgs(type)).stream().map(model -> {
            ModelUsage usage = usages.getOrDefault(model.getId(), ModelUsage.none(model.getId()));
            return new UsageResult(usage.modelId(), usage.count(), usage.external());
        }).toList();
    }

    /**
     * 读取不需要全局数据权限（M5）：列表本就返回全局模型的完整定义，详情、只读打开字段编辑器、复制都要读它
     */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('model:show','*')")
    public Object show(@PathVariable Long id) {
        Model bean = service.select(id);
        if (bean == null) {
            return Responses.notFound(Model.NOT_FOUND + id);
        }
        dataInSite(bean.getSiteId(), getCurrentSiteId());
        return bean;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('model:create','*')")
    @OperationLog(module = "model", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid Model bean) {
        Long siteId = getCurrentSiteId();
        User user = Contexts.getCurrentUser();
        Model model = new Model();
        // 只取操作员可定义的列：ID 由序列生成，order 走排序接口，来源三元组由种子导入器专管
        Entities.copyIncludes(bean, model, "name", "type", "scope", "mains", "asides", "customs");
        if (!Model.MODEL_TYPES.contains(model.getType())) {
            throw new Http400Exception("model type not allowed: " + model.getType());
        }
        if (Model.SINGLETON_MODEL_TYPES.contains(model.getType())) {
            throw new LogicException("error.model.singleton", model.getType());
        }
        validateScope(model.getScope());
        validateName(model.getName());
        globalPermission(model.isGlobal(), user.hasGlobalPermission());
        if (service.existsByName(model.getName(), model.getType(), model.isGlobal(), siteId, null)) {
            throw new LogicException("error.model.nameExist", model.getName());
        }
        ModelFieldRules.validateFieldSet(model.getMains());
        ModelFieldRules.validateFieldSet(model.getAsides());
        List<Model.Field> fields = ModelFieldRules.validateCustoms(model.getCustoms());
        if (model.isGlobal()) {
            validateDictScope(fields, Collections.emptyMap());
        }
        service.insert(model, siteId);
        return Responses.ok();
    }

    /**
     * 只写名称与共享范围（M2）。同名校验、改为本站私有的使用校验只在相应值变化时进行（M3）：
     * 历史数据里的重名不阻止无关的保存
     */
    @PutMapping
    @PreAuthorize("hasAnyAuthority('model:update','*')")
    @OperationLog(module = "model", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid Model bean) {
        Long siteId = getCurrentSiteId();
        User user = Contexts.getCurrentUser();
        Model model = service.select(bean.getId());
        if (model == null) {
            return Responses.notFound(Model.NOT_FOUND + bean.getId());
        }
        dataInSite(model.getSiteId(), siteId);
        boolean origGlobal = model.isGlobal();
        String origName = model.getName();
        Entities.copyIncludes(bean, model, "name", "scope");
        validateScope(model.getScope());
        validateName(model.getName());
        if (Model.SYSTEM_MODEL_IDS.contains(model.getId()) && !model.isGlobal()) {
            throw new Http400Exception("system model stays global: " + model.getId());
        }
        globalPermission(origGlobal || model.isGlobal(), user.hasGlobalPermission());
        boolean changed = !model.getName().equals(origName) || origGlobal != model.isGlobal();
        if (changed && service.existsByName(model.getName(), model.getType(), model.isGlobal(), siteId, model.getId())) {
            throw new LogicException("error.model.nameExist", model.getName());
        }
        // 全局改本站私有：其它站点的栏目、区块等仍绑定它时不允许——改后它们绑着一个别站的私有模型
        if (origGlobal && !model.isGlobal() && service.selectUsage(model.getId(), siteId).external() > 0) {
            throw new LogicException("error.model.scopeInUse");
        }
        // 本站私有改全局：已绑定的字典类型须是全局的，否则其它站点的该字段无选项可选（M8）
        if (!origGlobal && model.isGlobal()) {
            validateDictScope(model.getFieldList(), Collections.emptyMap());
        }
        service.update(model, siteId);
        return Responses.ok();
    }

    @PutMapping("{id}/system-fields")
    @PreAuthorize("hasAnyAuthority('model:update','*')")
    @OperationLog(module = "model", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateSystemFields(@PathVariable Long id, @RequestBody SystemFieldsParams params) {
        Model model = editable(id);
        ModelFieldRules.validateFieldSet(params.mains());
        ModelFieldRules.validateFieldSet(params.asides());
        service.updateSystemFields(model, params.mains(), params.asides());
        return Responses.ok();
    }

    @PutMapping("{id}/custom-fields")
    @PreAuthorize("hasAnyAuthority('model:update','*')")
    @OperationLog(module = "model", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateCustomFields(@PathVariable Long id, @RequestBody CustomFieldsParams params) {
        Model model = editable(id);
        List<Model.Field> fields = ModelFieldRules.validateCustoms(params.customs());
        if (model.isGlobal()) {
            validateDictScope(fields, model.getFieldMap());
        }
        service.updateCustomFields(model, params.customs());
        return Responses.ok();
    }

    @PutMapping("order")
    @PreAuthorize("hasAnyAuthority('model:update','*')")
    @OperationLog(module = "model", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateOrder(@RequestBody Long[] ids) {
        Long siteId = getCurrentSiteId();
        User user = Contexts.getCurrentUser();
        List<Model> list = new ArrayList<>();
        for (Long id : ids) {
            Model bean = service.select(id);
            if (bean == null) {
                return Responses.notFound(Model.NOT_FOUND + id);
            }
            dataInSite(bean.getSiteId(), siteId);
            globalPermission(bean.isGlobal(), user.hasGlobalPermission());
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    /**
     * 先校验每一行再一次性删除：任一行不可删（系统模型、仍被绑定、无权限），整批都不删
     */
    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('model:delete','*')")
    @OperationLog(module = "model", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        Long siteId = getCurrentSiteId();
        User user = Contexts.getCurrentUser();
        for (Long id : ids) {
            Model bean = service.select(id);
            if (bean == null) {
                return Responses.notFound(Model.NOT_FOUND + id);
            }
            dataInSite(bean.getSiteId(), siteId);
            globalPermission(bean.isGlobal(), user.hasGlobalPermission());
        }
        service.delete(ids);
        return Responses.ok();
    }

    private static ModelArgs visibleArgs(@Nullable String type) {
        return ModelArgs.of().scopeSiteId(getCurrentSiteId()).type(type);
    }

    /**
     * 取可编辑的模型：本站的或全局的；全局模型需要全局数据权限
     */
    private Model editable(Long id) {
        Model model = service.select(id);
        if (model == null) {
            throw new Http404Exception(Model.NOT_FOUND + id);
        }
        dataInSite(model.getSiteId(), getCurrentSiteId());
        globalPermission(model.isGlobal(), Contexts.getCurrentUser().hasGlobalPermission());
        return model;
    }

    private static void validateScope(Short scope) {
        if (scope != SCOPE_PRIVATE && scope != SCOPE_GLOBAL) {
            throw new Http400Exception("scope not allowed: " + scope);
        }
    }

    private static void validateName(@Nullable String name) {
        if (StringUtils.isBlank(name)) {
            throw new Http400Exception("name required");
        }
    }

    /**
     * 全局模型的字典类字段只能绑定全局字典类型（M8）。只看新增或改变了绑定的字段（M3）：
     * 既有行里早先绑定的本站字典类型不阻止无关的保存
     *
     * @param stored 库中现有字段，按代码索引；新建时为空
     */
    private void validateDictScope(List<Model.Field> fields, Map<String, Model.Field> stored) {
        for (Model.Field field : fields) {
            Long dictTypeId = field.getDictTypeId();
            if (dictTypeId == null || !ModelFieldRules.isDictBacked(field)) {
                continue;
            }
            Model.Field prev = stored.get(field.getCode());
            if (prev != null && Objects.equals(prev.getDictTypeId(), dictTypeId)) {
                continue;
            }
            DictType dictType = dictTypeService.select(dictTypeId);
            if (dictType == null || !dictType.isGlobal()) {
                throw new LogicException("error.model.fieldDictScope", field.getName());
            }
        }
    }

    /**
     * 系统字段集：主栏与侧栏的字段开关（控制台按模型类型的目录合并后的 JSON）
     */
    public record SystemFieldsParams(@Nullable String mains, @Nullable String asides) {
    }

    /**
     * 自定义字段集 JSON
     */
    public record CustomFieldsParams(@Nullable String customs) {
    }

    /**
     * 模型的使用统计：绑定它的行数，其中在当前站点之外的行数
     */
    public record UsageResult(Long modelId, int count, int external) {
    }
}
