package com.ujcms.cms.core.composition;

import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 页面构成健康报告（§11.5）。计算所得、从不持久化（派生状态不写回授权数据，§3.8）。聚合：
 * 注册表状态与近期重载、面板设置中失效的栏目/区块引用（component-settings Phase D 起取代
 * 卡片引用检查）、组件不可解析的面板、主题切换后的孤立/归档面板投放（可一键恢复）、
 * 结构树的孤立面板/悬空引用、空的已声明区域（仅提示）。
 *
 * @param registry             注册表状态（快照版本 + 近期重载）
 * @param stalePanelRefs       面板设置中的失效引用（栏目/区块/投票/文章引用所指目标已删除或越站，
 *                             {@link SettingsResolver#staleRefs} 口径）
 * @param literalSeedTokens    面板设置中未展开的种子令牌字面量（D9 之前导入的遗留，theme-seed-export R1——
 *                             导入对一切字符串无条件展开令牌，残留字面量必为遗留数据；修复走显式操作员动作）
 * @param unresolvableComponents 组件在命名空间链不可解析的面板（渲染为空占位）
 * @param orphanedPlacements   页面/区域不再被活动主题声明的在用面板投放（§19.3）
 * @param archivedPlacements   已归档面板投放（主题切换孤立，可恢复）
 * @param orphanPanels         区域面板投放，但既不被已发布树也不被草稿树引用（带外删除残留，page-regions §11）
 * @param danglingPanelRefs    结构树引用的面板ID没有对应的存活投放（带外删除残留，page-regions §11）
 * @param emptyRequiredRegions 已声明但无已发布行的区域（信息性，page-regions §11）
 * @param requiredSettingsMissing 已发布树内启用面板的必填设置为空（design-review P1-4 / B3——发布门
 *                             拦截草稿，此处捕获门上线前的存量与组件演化新增必填字段两种漏网路径）
 * @author PONY
 */
public record CompositionHealthReport(RegistryStatus registry,
                                      List<StalePanelRef> stalePanelRefs,
                                      List<LiteralSeedToken> literalSeedTokens,
                                      List<UnresolvableComponent> unresolvableComponents,
                                      List<OrphanPlacement> orphanedPlacements, List<OrphanPlacement> archivedPlacements,
                                      List<OrphanPanel> orphanPanels, List<DanglingPanelRef> danglingPanelRefs,
                                      List<EmptyRequiredRegion> emptyRequiredRegions,
                                      List<RequiredSettingMissing> requiredSettingsMissing) {

    /**
     * 注册表状态。
     *
     * @param snapshotVersion     快照版本（内容散列）
     * @param loadedAt            快照加载时间
     * @param shapeCount          Shape 数（全部主题 + 核心）
     * @param manifestCount       页面清单数（全部主题）
     * @param activeShapeCount    当前站点可解析的 Shape 数（活动主题 ∪ 核心，遮蔽同名计一次）
     * @param activeManifestCount 当前站点活动主题声明的页面清单数（按页面名去重）
     * @param recentReloads       近期重载审计（倒序）
     */
    public record RegistryStatus(String snapshotVersion, OffsetDateTime loadedAt, int shapeCount, int manifestCount,
                                 int activeShapeCount, int activeManifestCount, List<ReloadEntry> recentReloads) {
    }

    /**
     * 重载审计条目。
     *
     * @param source         触发来源
     * @param triggeredBy    触发人
     * @param result         结果（success/failure）
     * @param shapesCount    Shape 数
     * @param manifestsCount 页面清单数
     * @param diffSummary    变更摘要（可空）
     * @param errorDetail    失败详情（可空）
     * @param loadedAt       加载时间
     */
    public record ReloadEntry(String source, @Nullable String triggeredBy, String result, int shapesCount,
                              int manifestsCount, @Nullable String diffSummary, @Nullable String errorDetail,
                              OffsetDateTime loadedAt) {
    }

    /**
     * 面板设置中的失效引用（引用型设置所指目标已删除或越站，{@link SettingsResolver#staleRefs} 口径）。
     *
     * @param panelId   面板投放ID
     * @param pageRef   页面名
     * @param region    区域名（{@code slot_name_}）
     * @param component 组件名
     * @param setting   设置名
     * @param kind      目标种类（{@code channel} / {@code block} / {@code vote} / {@code article}）
     * @param targetRef 已失效的目标引用（ID 或别名）
     * @param origin    面板来源标记（{@code theme:<name>} = 主题种子面板——未改动者可经 导入/更新未修改项
     *                  自动修复，theme-marketplace D24；操作员面板为 {@code null}）
     */
    public record StalePanelRef(String panelId, String pageRef, String region,
                                String component, String setting, String kind, String targetRef,
                                @Nullable String origin) {
    }

    /**
     * 面板设置中未展开的种子令牌字面量（theme-seed-export R1）：channel/article/asset/theme 令牌在
     * D9（面板设置令牌展开）之前导入的站点上以字面量残留——渲染时该引用按未设值处理（静默断链），
     * 导出原样携带则空白站安装必失败。修复走「修复种子令牌」显式动作（{@link SeedTokenRepairer}）。
     *
     * @param panelId   面板投放ID
     * @param pageRef   页面名
     * @param region    区域名（{@code slot_name_}）
     * @param component 组件名
     * @param setting   设置路径（如 {@code channel} / {@code items[0].image.url}）
     * @param token     残留的令牌字面量
     */
    public record LiteralSeedToken(String panelId, String pageRef, String region,
                                   String component, String setting, String token) {
    }

    /**
     * 组件不可解析的面板：{@code panel_shape_} 在命名空间链（活动主题 ∪ core）解析不到 Shape，
     * 渲染为空占位。
     *
     * @param panelId   面板投放ID
     * @param pageRef   页面名
     * @param region    区域名
     * @param component 组件名
     */
    public record UnresolvableComponent(String panelId, String pageRef, String region,
                                        String component) {
    }

    /**
     * 孤立/归档面板投放。
     *
     * @param id        投放ID
     * @param pageRef   页面名
     * @param slotName  区域名
     * @param component 组件名（{@code panel_shape_}，可空）
     * @param title     组件实例标题（设置块 {@code title} 值，可空——操作员辨识用）
     */
    public record OrphanPlacement(String id, String pageRef, String slotName,
                                  @Nullable String component, @Nullable String title) {
    }

    /**
     * 孤立面板（page-regions §11）：区域面板投放（{@code panel_shape_} 非空），但其ID既不被该区域的
     * 已发布树也不被草稿树引用。结构树在每次保存时强校验，故仅会因带外删除（直接删投放）产生。
     *
     * @param id     面板投放ID
     * @param pageRef 页面名
     * @param region 区域名（{@code slot_name_}）
     */
    public record OrphanPanel(String id, String pageRef, String region) {
    }

    /**
     * 悬空面板引用（page-regions §11）：结构树（已发布或草稿）引用的面板ID没有对应的存活面板投放。
     *
     * @param pageRef 页面名
     * @param region  区域名
     * @param panelId 树中悬空的面板ID
     */
    public record DanglingPanelRef(String pageRef, String region, String panelId) {
    }

    /**
     * 空的已声明区域（page-regions §11，信息性）：清单声明了区域但已发布树无行。
     *
     * @param pageRef 页面名
     * @param region  区域名
     * @param themes  声明该区域的命名空间（主题键）
     */
    public record EmptyRequiredRegion(String pageRef, String region, List<String> themes) {
    }

    /**
     * 必填设置为空（design-review P1-4 / B3）：<b>已发布</b>树引用的启用面板，其组件声明
     * {@code required} 的设置值为空（{@code FieldValueValidator.isEmpty} 口径，与发布门一致）——
     * 渲染期被缺省值静默补齐或渲染为空。草稿不在此列（发布门拦截）。
     *
     * @param panelId   面板投放ID
     * @param pageRef   页面名
     * @param region    区域名
     * @param scope     区域范围（{@code default} / 覆盖范围值）
     * @param component 组件名
     * @param setting   必填而为空的设置名
     */
    public record RequiredSettingMissing(String panelId, String pageRef, String region,
                                         String scope, String component, String setting) {
    }
}
