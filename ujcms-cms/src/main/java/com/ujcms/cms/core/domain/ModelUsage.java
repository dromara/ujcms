package com.ujcms.cms.core.domain;

/**
 * 模型的使用统计（docs/spec/model-management/plan.md M4）：有多少行绑定了它，其中多少行在当前站点之外。
 * 控制台据此显示“使用情况”、禁用不可删除的行、拒绝把其它站点仍在用的全局模型改为本站私有
 *
 * @param modelId  模型ID
 * @param count    绑定该模型的行数（栏目按文章模型、栏目模型各计一次；区块、专题、表单类型、站点各计一次）
 * @param external 其中属于当前站点之外的行数（全局区块的 {@code site_id_} 为空，也算外部）
 * @author PONY
 */
public record ModelUsage(Long modelId, int count, int external) {
    public static ModelUsage none(Long modelId) {
        return new ModelUsage(modelId, 0, 0);
    }
}
