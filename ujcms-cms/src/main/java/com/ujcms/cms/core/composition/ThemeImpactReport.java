package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.ThemeContentSeedImporter.DeclaredContent;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 主题激活/切换影响报告（§3.4 / §12.3 / §6.2）。在切换前对“将要生效的桌面 + 移动主题对”做影响分析：
 * 孤立面板投放（页面/区域不再被任一主题声明——激活时一律归档，D20）、将自动恢复的归档投放
 * （页面/区域重新被声明，D21）、孤立模板选择（显式模板选择在目标主题对中无对应模板文件，
 * template-track-variants D7），以及可导入的新种子。操作员据此选择种子导入范围
 * （三态 all/composition/none，§6.3）。
 * <b>区域名是站点级契约</b>：仅当两个生效主题<b>都不</b>声明该 (page, region) 时才算孤立（§4 #19）。
 *
 * @param resultingThemes            切换后生效的主题键（桌面 + 移动，去重）
 * @param orphanedPlacements         孤立面板投放（激活时归档）
 * @param restorablePlacements       将自动恢复的归档投放数（页面/区域被切换后生效主题对重新声明，
 *                                   激活时取消归档——切换往返零手工，D21）
 * @param orphanedTemplateSelections 孤立模板选择数（显式 {@code *_template_} 选择在切换后生效主题对的
 *                                   模板文件并集中缺失的选择列计数；只计数不改写——切回主题即恢复，
 *                                   template-track-variants D7）
 * @param seedAvailable              目标主题是否带可导入种子
 * @param seedImported               该主题种子是否已导入过
 * @param seedContent                种子声明的内容条目数（栏目/文章/专题——三态提示的计数来源，§6.2 / A3）
 * @param demoDataPresent            本主题的本源行（内容 + 构成表任一）是否仍在库——为真且重新激活时
 *                                   提示「刷新未修改的演示数据」（syncUnmodified，§6.3）
 * @param themeShapes                目标主题声明的 Shape 数（展示用）
 * @param themePages                 目标主题声明的页面数（展示用）
 * @param settingsGroups             目标主题设置分组数（展示用）
 * @author PONY
 */
public record ThemeImpactReport(List<String> resultingThemes, List<OrphanedPlacement> orphanedPlacements,
                                int restorablePlacements,
                                int orphanedTemplateSelections,
                                boolean seedAvailable, boolean seedImported,
                                DeclaredContent seedContent, boolean demoDataPresent,
                                int themeShapes, int themePages, int settingsGroups) {
    /**
     * 孤立面板投放。
     *
     * @param id        投放ID
     * @param pageRef   页面名
     * @param slotName  区域名
     * @param component 组件名（{@code panel_shape_}，可空）
     * @param title     组件实例标题（设置块 {@code title} 值，可空——操作员辨识用，如「热点新闻」）
     */
    public record OrphanedPlacement(Long id, String pageRef, String slotName,
                                    @Nullable String component, @Nullable String title) {
    }
}
