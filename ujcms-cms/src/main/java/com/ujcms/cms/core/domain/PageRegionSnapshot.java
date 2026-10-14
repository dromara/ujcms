package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.generated.GeneratedPageRegionSnapshot;

import java.util.Objects;

/**
 * 页面区域发布快照实体类（区域历史，只追加）。每行物化一次已发布状态：布局树 + 逐面板
 * {@code shape/settings/enabled}（自包含、不含面板ID——面板池被孤立清理改写，ID 引用会腐坏）。
 * 以行坐标 {@code (site, page_ref, region_name, scope)} 为键、不建 {@code ujcms_page_region}
 * 外键：覆盖行删除后仍可凭快照重建（docs/spec/region-history/plan.md §3/§6）。
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class PageRegionSnapshot extends GeneratedPageRegionSnapshot {
    /**
     * 来源：常规发布
     */
    public static final String SOURCE_PUBLISH = "publish";
    /**
     * 来源：恢复（本快照是恢复动作发布的新状态，{@code restored_from_} 指向被恢复的快照）
     */
    public static final String SOURCE_RESTORE = "restore";
    /**
     * 来源：回填（发布/恢复/删除时发现当前已发布树未被最新快照覆盖——特性上线前的存量状态或
     * 种子导入直写的树——先补记再继续）
     */
    public static final String SOURCE_BACKFILL = "backfill";
    /**
     * 来源：删除覆盖行（revert-to-default 前捕获被销毁的已发布树）
     */
    public static final String SOURCE_DELETE = "delete";
    /**
     * 来源：还原演示数据（seed {@code resetAllOrigin} 重建本源区域后发布的种子默认树；
     * 被替换的已发布树/未发布草稿由前置回填快照保全——重置本身可经历史恢复，theme-marketplace D22）
     */
    public static final String SOURCE_SEED_RESET = "seed-reset";

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PageRegionSnapshot bean = (PageRegionSnapshot) o;
        return Objects.equals(getId(), bean.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
