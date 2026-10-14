package com.ujcms.cms.core.composition;

import org.jspecify.annotations.Nullable;

/**
 * 导出行的来源分类（theme-seed-export §4）：来源三元组把每个入选行归入四类之一——
 * 这正是导出成为<b>迭代闭环</b>而非一次性快照的机制。分类为纯函数（只读，D2）。
 *
 * @author PONY
 */
enum SeedOriginClass {
    /**
     * 第 1 类：本主题来源且散列复现——原样重发同一 originId（与原种子按构造字节一致）
     */
    ORIGIN_UNMODIFIED,
    /**
     * 第 2 类：本主题来源但操作员改过——以当前投影重发、保留 originId
     * （已装站点在 syncUnmodified 语义下随主题更新收到改动）
     */
    ORIGIN_MODIFIED,
    /**
     * 第 3 类：操作员行（无来源标记）——铸造 originId（纯函数，不写库；adopt 属 E2）
     */
    OPERATOR_NEW,
    /**
     * 第 4 类：他主题来源行——按第 3 类处理：铸造全新 originId，<b>绝不认领或改写他源三元组</b>（D3）
     */
    FOREIGN_ORIGIN;

    /**
     * 按来源三元组分类。「操作员未改动」判定与导入侧同一口径（R4）：散列缺失视作未改。
     *
     * @param rowOrigin   行上来源标记（可空）
     * @param ourOrigin   本主题来源标记（{@code theme:<name>}）
     * @param storedHash  行上 {@code origin_hash_}（可空——早于 R4 的导入）
     * @param currentHash 自当前行重导出的投影散列
     */
    static SeedOriginClass classify(@Nullable String rowOrigin, String ourOrigin,
                                    @Nullable String storedHash, String currentHash) {
        if (rowOrigin == null || rowOrigin.isEmpty()) {
            return OPERATOR_NEW;
        }
        if (!ourOrigin.equals(rowOrigin)) {
            return FOREIGN_ORIGIN;
        }
        return storedHash == null || storedHash.equals(currentHash) ? ORIGIN_UNMODIFIED : ORIGIN_MODIFIED;
    }

    /**
     * 是否保留行上既有 originId（第 1/2 类）
     */
    boolean keepsOriginId() {
        return this == ORIGIN_UNMODIFIED || this == ORIGIN_MODIFIED;
    }
}
