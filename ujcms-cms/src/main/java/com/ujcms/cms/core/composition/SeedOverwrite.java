package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * 种子覆盖策略（theme-marketplace §6.1 政策词汇）。作用于「重导入 / 主题更新 / 手动导入」，
 * 决定种子如何对待<b>已存在的本源行</b>（{@code origin_ = theme:<name>}）；
 * 非本源行（操作员行、linked 行）在任何策略下都不被触碰（§6.1 安全不变量 1）；唯一例外是
 * <b>他主题本源区域行</b>（上一主题演示布局的遗留，D23）：{@code resetAllOrigin} 下恒重建，其余策略下当
 * R4 散列证明其自上一主题导入后未被操作员改动时重建——均改记为本主题来源。
 *
 * <p>「操作员未改动」判定即 R4 单一脏检测：以当前行重导出种子形投影复现 {@code origin_hash_}。
 * 散列缺失的行（导入早于 R4）视作未改——无从判定，宁可少报。
 *
 * @author PONY
 */
public enum SeedOverwrite {
    /**
     * 只填空（缺省）：既有本源行一律跳过——A2 的非破坏行为（他主题遗留且散列证明未改动的区域行
     * 视作「空位」重建，D23）
     */
    FILL_ONLY,
    /**
     * 同步未改动：仅刷新「操作员未改动」的本源行（散列证明未偏离），改过的跳过
     */
    SYNC_UNMODIFIED,
    /**
     * 重置全部本源行：无条件以种子覆盖每个本源行（红色警示确认；仍绝不触碰操作员行；他主题本源
     * 区域行一并重建为本主题演示布局，D23）
     */
    RESET_ALL_ORIGIN;

    /**
     * 是否应覆盖一个已存在的本源行。
     *
     * @param unmodified 该行是否「操作员未改动」（R4 散列判定）
     */
    public boolean shouldOverwrite(boolean unmodified) {
        return switch (this) {
            case FILL_ONLY -> false;
            case SYNC_UNMODIFIED -> unmodified;
            case RESET_ALL_ORIGIN -> true;
        };
    }

    /**
     * JSON / 表单值使用小驼峰（{@code fillOnly|syncUnmodified|resetAllOrigin}）
     */
    @JsonCreator
    public static SeedOverwrite parse(String value) {
        return switch (value) {
            case "fillOnly" -> FILL_ONLY;
            case "syncUnmodified" -> SYNC_UNMODIFIED;
            case "resetAllOrigin" -> RESET_ALL_ORIGIN;
            default -> throw new IllegalArgumentException(
                    "overwrite must be fillOnly|syncUnmodified|resetAllOrigin: " + value);
        };
    }

    @JsonValue
    public String toJson() {
        return switch (this) {
            case FILL_ONLY -> "fillOnly";
            case SYNC_UNMODIFIED -> "syncUnmodified";
            case RESET_ALL_ORIGIN -> "resetAllOrigin";
        };
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
