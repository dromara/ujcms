package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.PageRegionSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 页面区域发布快照 Mapper（区域历史，只追加——无 update 语句）
 *
 * @author PONY
 */
@Mapper
@Repository
public interface PageRegionSnapshotMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(PageRegionSnapshot bean);

    /**
     * 删除数据（仅保留裁剪用）
     *
     * @param id 快照ID
     * @return 删除条数
     */
    int delete(Long id);

    /**
     * 删除站点全部发布快照（站点删除级联，docs/spec/site-delete-cascade/plan.md——
     * 「只追加」针对存续站点内的历史；站点删除后恢复端点不可达，快照随站点清除）
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 获取数据
     *
     * @param id 快照ID
     * @return 实体对象
     */
    @Nullable
    PageRegionSnapshot select(Long id);

    /**
     * 坐标下的全部快照（新→旧，按 {@code id_} 倒序——雪花ID单调；含 {@code content_json_}，
     * 行数受保留上限约束、体量小）
     *
     * @param siteId     站点ID
     * @param pageRef    页面清单名称
     * @param regionName 区域插槽名称
     * @param scope      区域范围
     * @return 快照列表
     */
    List<PageRegionSnapshot> listByCoordinates(@Param("siteId") Long siteId, @Param("pageRef") String pageRef,
                                               @Param("regionName") String regionName,
                                               @Param("scope") String scope);
}
