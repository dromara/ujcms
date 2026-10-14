package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.ThemeState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 主题构成状态 Mapper（页面构成）。每 {@code (站点, 主题)} 一行，按需懒创建。
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ThemeStateMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(ThemeState bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(ThemeState bean);

    /**
     * 删除数据
     *
     * @param id 主题状态ID
     * @return 删除条数
     */
    int delete(Long id);

    /**
     * 删除站点全部主题状态行（站点删除级联，docs/spec/site-delete-cascade/plan.md）
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 获取数据
     *
     * @param id 主题状态ID
     * @return 实体对象
     */
    @Nullable
    ThemeState select(Long id);

    /**
     * 按唯一键获取主题状态（{@code uk_theme_state}）
     *
     * @param siteId    站点ID
     * @param themeName 主题名称
     * @return 实体对象
     */
    @Nullable
    ThemeState selectByUnique(@Param("siteId") Long siteId, @Param("themeName") String themeName);

    /**
     * 站点内全部主题状态
     *
     * @param siteId 站点ID
     * @return 主题状态列表
     */
    List<ThemeState> listBySiteId(Long siteId);
}
