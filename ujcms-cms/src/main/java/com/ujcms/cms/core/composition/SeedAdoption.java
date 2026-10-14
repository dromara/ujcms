package com.ujcms.cms.core.composition;

import org.jspecify.annotations.Nullable;

/**
 * 待认领行（theme-seed-export §4.2 adopt 模式）：导出分类为第 3 类（操作员行，无来源标记）时，
 * 引擎把铸造的来源三元组作为<b>候选</b>随文件束返回——引擎本身绝不写库（D2）；
 * {@code adoptOrigin=true} 时由 E2 的 {@link SeedOriginAdopter} 回写（D2 的唯一豁免通道）。
 * 他源行（第 4 类）恒不入列（D3：绝不认领他源）。
 *
 * <p>面板的 {@code originId} 为导入侧再生方案 {@code <page>#<region>#<track>#<rowId>#<col>#<panel>}
 * 按<b>发射后</b>位置合成（被跳过的悬挂面板不占位）——认领后的行与本次导出种子的自导入逐行对应。
 * 区域行无 {@code origin_id_} 列，{@code originId} 为 {@code null}。
 *
 * @param category   类别
 * @param id         行ID
 * @param originId   铸造的 originId（区域为 {@code null}）
 * @param originHash 当前投影散列（认领即钉住「未改动」基线——下次导出归第 1 类）
 * @author PONY
 */
public record SeedAdoption(Category category, Long id, @Nullable String originId, String originHash) {

    /**
     * 认领类别（内容四类 + 构成两类——§11 决定构成行同样认领；E4a 增区块定义。
     * 全局区块恒不入列，D26）
     */
    public enum Category {
        /** 栏目 */
        CHANNEL,
        /** 文章 */
        ARTICLE,
        /** 专题 */
        TOPIC,
        /** 区块定义（E4a；仅本站私有行） */
        BLOCK,
        /** 投票定义（E4b；投票恒本站私有） */
        VOTE,
        /** 问卷定义（E4d；问卷恒本站私有） */
        SURVEY,
        /** 表单类型定义（E4e；表单恒本站私有） */
        FORM_TYPE,
        /** 链接组（E4f；分组恒本站私有——链接随定义，无独立认领） */
        LINK_GROUP,
        /** 模型定义（model-seeding D1；仅本站私有行——全局模型恒不入列） */
        MODEL,
        /** 字典类型定义（model-seeding D8；仅本站私有非 sys 行） */
        DICT,
        /** 区域面板投放 */
        PANEL,
        /** 区域（结构树包裹行，无 origin_id_ 列） */
        REGION
    }
}
