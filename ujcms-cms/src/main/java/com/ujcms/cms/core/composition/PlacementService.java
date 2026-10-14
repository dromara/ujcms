package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.PageManifest.SlotDef;
import com.ujcms.cms.core.domain.Placement;
import com.ujcms.cms.core.mapper.PlacementMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.exception.Http400Exception;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 面板投放服务（页面构成）。投放行 = 区域内的一个面板实例（{@code panel_shape_} + {@code settings_json_}，
 * component-settings §3；结构树在 {@code ujcms_page_region.layout_json_}）。渲染期解析在
 * {@link PanelResolver}；本服务只负责面板行的保存/删除/归档与反向查询。
 * 经典插槽的解析/保存路径已随插槽子系统于 Phase D 退役。
 *
 * @author PONY
 */
@Service
public class PlacementService {
    private final PlacementMapper mapper;
    private final CompositionCacheSync cacheSync;
    private final SnowflakeSequence snowflakeSequence;

    public PlacementService(PlacementMapper mapper, CompositionCacheSync cacheSync,
                            SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.cacheSync = cacheSync;
        this.snowflakeSequence = snowflakeSequence;
    }

    /**
     * 保存区域面板投放（page-regions §7.3）。面板以 {@code slot_name_ = region_name_} 多对一存储、
     * 由 {@code id_} 区分。校验面板组件在区域 {@code allowedShapes} 内。
     *
     * <p><b>归属校验（更新路径）</b>：面板 {@code id} 来自客户端草稿树，更新按裸 {@code id_} 全列覆写——
     * 必须先证实该行确属本 {@code (site, page, region)} 且确为面板行，否则持有 A 站区域权限的
     * 请求可用任意 {@code id} 覆写任何站点的任意投放。不匹配 → 400。
     * 同时保留既有行的种子来源（{@code origin_}/{@code origin_id_}）与归档标记，面板编辑不清洗 provenance。
     *
     * <p><b>未知ID重建</b>：{@code id} 在库中不存在（面板已被此前保存的孤立清理硬删——构建器撤销
     * 「删除面板/行」的快照重放，visual-builder D8）时，按负载配置重建为新投放并返回新雪花ID，
     * 而非 400；构建器经 {@code saveDraft} 返回的 {@code panels[]} 遍历序回填新ID。
     *
     * @param bean          面板投放（{@code panelShape}/{@code settings} 已设置；新增时 id 为空）
     * @param regionSlotDef 所在区域的定义（取 allowedShapes 校验）
     * @return 面板投放 ID（新增时为新分配的雪花 ID）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long savePanel(Placement bean, SlotDef regionSlotDef) {
        validate(bean, regionSlotDef);
        boolean isNew = bean.getId() == null || bean.getId() == 0L;
        OffsetDateTime now = OffsetDateTime.now();
        bean.setModified(now);
        bean.disassemble();
        if (isNew) {
            bean.setId(snowflakeSequence.nextId());
            bean.setCreated(now);
            mapper.insert(bean);
        } else {
            Placement existed = mapper.select(bean.getId());
            if (existed == null) {
                // 撤销「删除面板/行」的快照重放（visual-builder D8）：草稿树携带的面板ID已被此前保存
                // 的孤立清理硬删。未知ID按负载配置重建为新投放（新雪花ID，经 saveDraft panels[] 按遍历
                // 序回填构建器树；不复用客户端ID，防ID占位/未来碰撞）。ID存在但归属不符仍是 400——
                // 跨站/跨区域覆写防线不动。
                bean.setId(snowflakeSequence.nextId());
                bean.setCreated(now);
                mapper.insert(bean);
            } else if (existed.getPanelShape() == null
                    || !Objects.equals(existed.getSiteId(), bean.getSiteId())
                    || !Objects.equals(existed.getPageRef(), bean.getPageRef())
                    || !Objects.equals(existed.getSlotName(), bean.getSlotName())) {
                throw new Http400Exception("Panel not found in region: " + bean.getId());
            } else {
                bean.setOrigin(existed.getOrigin());
                bean.setOriginId(existed.getOriginId());
                // 保留导入时刻散列（R4）：面板编辑不重算，行内容偏离散列即「操作员已改」
                bean.setOriginHash(existed.getOriginHash());
                bean.setArchivedAt(existed.getArchivedAt());
                mapper.update(bean);
            }
        }
        cacheSync.invalidateAll("placement");
        return bean.getId();
    }

    /**
     * 面板写入面校验（Shape ∈ 区域 {@code allowedShapes}、显示设备三值）——{@link #savePanel} 的前置校验；
     * 区域草稿保存对内容未变的已发布面板跳过写库（写时复制，visual-builder shell-polish D20）时单独调用，
     * 保持「每次保存复验全部面板」的不变量。
     */
    public void validate(Placement bean, SlotDef regionSlotDef) {
        validatePanelShape(bean, regionSlotDef);
        validateVisibility(bean);
    }

    private void validatePanelShape(Placement bean, SlotDef regionSlotDef) {
        String shape = bean.getPanelShape();
        if (shape == null || shape.isBlank()) {
            throw new Http400Exception("Panel shape required");
        }
        if (!regionSlotDef.getAllowedShapes().contains(shape)) {
            throw new Http400Exception("Panel shape not allowed by region: " + shape);
        }
    }

    /**
     * 显示设备合法性（design-review P2-5）：写入面必须是三值之一（读取面另有宽容归一）。
     */
    private static void validateVisibility(Placement bean) {
        String visibility = bean.getVisibility();
        if (!Placement.VISIBILITY_ALL.equals(visibility) && !Placement.VISIBILITY_DESKTOP.equals(visibility)
                && !Placement.VISIBILITY_MOBILE.equals(visibility)) {
            throw new Http400Exception("Unknown visibility: " + visibility);
        }
    }

    /**
     * 删除投放
     */
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        int count = mapper.delete(id);
        cacheSync.invalidateAll("placement");
        return count;
    }

    /**
     * 软归档（主题切换孤立，Phase 5 起由影响分析调用；渲染期视同不存在）
     */
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        Placement bean = require(id);
        bean.setArchivedAt(OffsetDateTime.now());
        bean.setModified(OffsetDateTime.now());
        mapper.update(bean);
        cacheSync.invalidateAll("placement");
    }

    /**
     * 取消归档
     */
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id) {
        Placement bean = require(id);
        bean.setArchivedAt(null);
        bean.setModified(OffsetDateTime.now());
        mapper.update(bean);
        cacheSync.invalidateAll("placement");
    }

    @Nullable
    public Placement select(Long id) {
        return mapper.select(id);
    }

    /**
     * 页面内全部投放
     */
    public List<Placement> listByPage(Long siteId, String pageRef) {
        return mapper.listByPage(siteId, pageRef);
    }

    /**
     * 站点内全部投放（健康页 / 主题影响分析）
     */
    public List<Placement> listBySiteId(Long siteId) {
        return mapper.listBySiteId(siteId);
    }

    private Placement require(Long id) {
        Placement bean = mapper.select(id);
        if (bean == null) {
            throw new Http400Exception(Placement.NOT_FOUND + id);
        }
        return bean;
    }
}
