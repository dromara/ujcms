package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.Dict;
import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.mapper.DictMapper;
import com.ujcms.cms.core.mapper.DictTypeMapper;
import com.ujcms.cms.core.service.args.DictTypeArgs;
import com.ujcms.cms.core.service.args.ModelArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.web.exception.LogicException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 字典服务契约（dictionary plan P1）：系统行不可删（服务层，种子路径同受约束）；
 * {@code listByAlias} 的可见范围 = 本站 ∪ 全局。
 */
class DictServiceTest {
    private DictMapper mapper;
    private DictTypeMapper typeMapper;
    private ModelService modelService;
    private DictService service;
    private DictTypeService typeService;

    @BeforeEach
    void setUp() {
        mapper = mock(DictMapper.class);
        typeMapper = mock(DictTypeMapper.class);
        modelService = mock(ModelService.class);
        service = new DictService(mapper, mock(SnowflakeSequence.class));
        typeService = new DictTypeService(typeMapper, mapper, modelService, mock(SnowflakeSequence.class));
    }

    private static Dict dict(long id, boolean sys) {
        Dict bean = new Dict();
        bean.setId(id);
        bean.setTypeId(2L);
        bean.setValue("v" + id);
        bean.setSys(sys);
        return bean;
    }

    @Test
    void deleteRefusesSysEntry() {
        when(mapper.select(1L)).thenReturn(dict(1L, true));
        assertThrows(LogicException.class, () -> service.delete(1L));
        verify(mapper, never()).delete(1L);
    }

    @Test
    void deleteRemovesPlainEntryAndIgnoresMissing() {
        when(mapper.select(3L)).thenReturn(dict(3L, false));
        when(mapper.delete(3L)).thenReturn(1);
        assertEquals(1, service.delete(3L));
        assertEquals(0, service.delete(99L));
        verify(mapper, never()).delete(99L);
    }

    @Test
    void listByAliasIsScopedToSiteOrGlobalAndFiltersByName() {
        service.listByAlias("sys_site_lang", "中文", 7L);
        ArgumentCaptor<QueryInfo> captor = ArgumentCaptor.forClass(QueryInfo.class);
        verify(mapper).selectAll(captor.capture());
        QueryInfo info = captor.getValue();
        assertTrue(info.getJoinTables().stream().anyMatch(j -> "ujcms_dict_type".equals(j.getTableName())));
        assertEquals(2, info.getWhereOrAndConditions().get("0scope").size(), "site OR global");
        assertTrue(info.getWhereConditions().stream().anyMatch(c -> "sys_site_lang".equals(c.getValue())));
        // Contains 条件的值被包成 LIKE 模式（%中文%）
        assertTrue(info.getWhereConditions().stream()
                .anyMatch(c -> c.getValue() instanceof String s && s.contains("中文")));
    }

    @Test
    void listByAliasWithoutNameAddsNoNameCondition() {
        service.listByAlias("sys_site_lang", null, 7L);
        ArgumentCaptor<QueryInfo> captor = ArgumentCaptor.forClass(QueryInfo.class);
        verify(mapper).selectAll(captor.capture());
        assertFalse(captor.getValue().getWhereConditions().stream()
                .anyMatch(c -> c.getColumn() != null && c.getColumn().contains("name")));
    }

    @Test
    void existsByValueDelegatesToMapper() {
        when(mapper.existsByValue(2L, "en")).thenReturn(1);
        assertTrue(service.existsByValue(2L, "en"));
        assertFalse(service.existsByValue(2L, "fr"));
    }

    /** 模型的自定义字段绑定字典类型：绑定存于 customs JSON 内 */
    private static Model modelBinding(long dictTypeId) {
        Model model = new Model();
        model.setId(30L);
        model.setName("产品");
        model.setCustoms("[{\"code\":\"title2\",\"name\":\"副标题\",\"type\":\"text\"},"
                + "{\"code\":\"brand\",\"name\":\"品牌\",\"type\":\"select\",\"dictTypeId\":\"" + dictTypeId + "\"}]");
        return model;
    }

    @Test
    void typeDeleteRefusesSysTypeAndTypeBoundByModelField() {
        DictType sys = new DictType();
        sys.setId(1L);
        sys.setSys(true);
        when(typeMapper.select(1L)).thenReturn(sys);
        assertThrows(LogicException.class, () -> typeService.delete(1L));

        DictType bound = new DictType();
        bound.setId(5L);
        when(typeMapper.select(5L)).thenReturn(bound);
        when(modelService.selectList(any())).thenReturn(List.of(modelBinding(5L)));
        LogicException thrown = assertThrows(LogicException.class, () -> typeService.delete(5L));
        assertEquals("error.dictType.inUse", thrown.getCode());
        verify(mapper, never()).deleteByTypeId(any());
        verify(typeMapper, never()).delete(any());
    }

    @Test
    void typeDeleteCascadesEntriesBeforeTheType() {
        DictType free = new DictType();
        free.setId(6L);
        when(typeMapper.select(6L)).thenReturn(free);
        when(typeMapper.delete(6L)).thenReturn(1);
        // 别的类型的绑定不妨碍删除
        when(modelService.selectList(any())).thenReturn(List.of(modelBinding(5L)));
        assertEquals(1, typeService.delete(List.of(6L, 404L)));
        InOrder inOrder = inOrder(mapper, typeMapper);
        inOrder.verify(mapper).deleteByTypeId(6L);
        inOrder.verify(typeMapper).delete(6L);
    }

    @Test
    void listBindingsScansEveryModelNotOnlyTheCurrentSite() {
        when(modelService.selectList(any())).thenReturn(List.of(modelBinding(5L)));
        List<DictTypeService.Binding> bindings = typeService.listBindings(5L);
        assertEquals(1, bindings.size());
        assertEquals("brand", bindings.get(0).field().getCode());
        ArgumentCaptor<ModelArgs> captor = ArgumentCaptor.forClass(ModelArgs.class);
        verify(modelService).selectList(captor.capture());
        assertTrue(captor.getValue().getQueryMap().isEmpty(), "no site scope: other sites' models can bind a global type");
    }

    @Test
    void typeListIsGlobalFirstAndCountsEntriesOnlyOnRequest() {
        typeService.selectList(DictTypeArgs.of());
        typeService.selectList(DictTypeArgs.of().queryEntryCount());
        ArgumentCaptor<QueryInfo> captor = ArgumentCaptor.forClass(QueryInfo.class);
        InOrder inOrder = inOrder(typeMapper);
        inOrder.verify(typeMapper).selectAll(captor.capture(), eq(false));
        inOrder.verify(typeMapper).selectAll(any(), eq(true));
        QueryInfo.OrderByCondition first = captor.getValue().getOrderByConditions().get(0);
        assertTrue(first.getColumn().contains("scope"), first.getColumn());
        assertEquals("desc", first.getDirection());
    }
}
