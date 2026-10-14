package com.ujcms.cms.core.composition;

import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * 主题种子导出选择（theme-seed-export §3）。导出恒为「选择 + 依赖闭包」，绝非整站缺省：
 * <ul>
 *   <li>{@code channelIds} 为 {@code null} 时取<b>缺省选择</b>——本主题来源栏目
 *       （{@code origin_ = theme:<name>}）+ 其祖先闭包；显式给定即向导树的勾选结果，
 *       操作员/他主题来源行也随勾选导出（§4 第 3/4 类，铸造 originId）。</li>
 *   <li>{@code topicIds} 同理（缺省 = 本主题来源专题）。</li>
 *   <li>{@code articleCapPerChannel}：每栏目文章上限，缺省 20（最近发布优先）；
 *       构成引用的文章与本主题来源文章不受上限约束（D6：闭包优先于上限）。</li>
 *   <li>{@code includeDrafts}：是否导出草稿文章（缺省否——演示草稿合法但少见，§3）。</li>
 *   <li>{@code includeContent} / {@code includeComposition}：导出范围
 *       （§8 请求体 {@code scope: content|composition|all} 的引擎形）。</li>
 * </ul>
 *
 * @param channelIds            显式选择的栏目ID集合；{@code null} = 缺省选择
 * @param topicIds              显式选择的专题ID集合；{@code null} = 缺省选择
 * @param surveyIds             显式选择的问卷ID集合；{@code null} = 缺省选择（本主题来源问卷；
 *                              问卷无引用面，显式多选是操作员问卷入包的唯一通道，E4d / D35）
 * @param formTypeIds           显式选择的表单类型ID集合；{@code null} = 缺省选择（本主题来源表单；
 *                              表单无引用面，显式多选是操作员表单入包的唯一通道，E4e / D40）
 * @param linkGroupIds     显式选择的链接组ID集合；{@code null} = 缺省选择（本主题来源
 *                              分组；友链无引用面，显式多选是操作员分组入包的唯一通道，E4f / D43）
 * @param articleCapPerChannel  每栏目文章上限（≥ 0；构成引用与来源行不受限，D6）
 * @param includeDrafts         是否导出草稿文章
 * @param includeContent        是否导出内容种子（栏目/文章/专题/区块条目 + 资源）
 * @param includeComposition    是否导出构成种子（区域/面板）
 * @author PONY
 */
public record SeedExportRequest(@Nullable Set<Long> channelIds, @Nullable Set<Long> topicIds,
                                @Nullable Set<Long> surveyIds, @Nullable Set<Long> formTypeIds,
                                @Nullable Set<Long> linkGroupIds,
                                int articleCapPerChannel, boolean includeDrafts,
                                boolean includeContent, boolean includeComposition) {

    /**
     * 缺省每栏目文章上限（§3 / D6）
     */
    public static final int DEFAULT_ARTICLE_CAP = 20;

    /**
     * 缺省选择：来源行 + 构成引用 + 闭包，上限 20，不含草稿，范围 all。
     */
    public static SeedExportRequest defaults() {
        return new SeedExportRequest(null, null, null, null, null, DEFAULT_ARTICLE_CAP, false, true, true);
    }
}
