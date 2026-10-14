package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.ThemePackage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 主题包 Mapper（install-flow §6 P3）。全局表，无站点维度。
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ThemePackageMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(ThemePackage bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(ThemePackage bean);

    /**
     * 删除数据
     *
     * @param id 主键ID
     * @return 删除条数
     */
    int delete(Long id);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    ThemePackage select(Long id);

    /**
     * 按名称+版本获取（同名同版本唯一）
     *
     * @param name    主题名称
     * @param version 版本
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    ThemePackage selectByNameAndVersion(@Param("name") String name, @Param("version") String version);

    /**
     * 全量列表（名称升序、创建时间降序）
     */
    List<ThemePackage> list();

    /**
     * 按名称列出（创建时间降序——首条即最新入库版本）
     *
     * @param name 主题名称
     */
    List<ThemePackage> listByName(String name);

    /**
     * 按市场条目标识列出（创建时间降序）
     *
     * @param marketplaceSlug 市场条目标识
     */
    List<ThemePackage> listBySlug(String marketplaceSlug);
}
