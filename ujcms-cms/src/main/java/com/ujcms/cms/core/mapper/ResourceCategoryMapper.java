package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.ResourceCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 资源库分类 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ResourceCategoryMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(ResourceCategory bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(ResourceCategory bean);

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
    ResourceCategory select(Long id);

    /**
     * 获取站点的分类列表，按 order_ 升序。{@code ownerId} 为空获取共享分类（owner_id_ 为空），
     * 否则获取该用户的个人分类（owner_id_ = ownerId）。
     *
     * @param siteId  站点ID
     * @param ownerId 所有者用户ID，{@code null} 表示共享分类
     * @return 数据列表
     */
    List<ResourceCategory> listBySiteId(@Param("siteId") Long siteId, @Param("ownerId") @Nullable Long ownerId);

    /**
     * 根据名称查找共享分类（用于查找/创建“未分类”）
     *
     * @param siteId   站点ID
     * @param parentId 上级分类ID，可为 {@code null}
     * @param name     分类名称
     * @return 数据列表
     */
    List<ResourceCategory> findByName(@Param("siteId") Long siteId, @Param("parentId") @Nullable Long parentId,
                                      @Param("name") String name);

    /**
     * 获取同级最大排序值
     *
     * @param siteId   站点ID
     * @param parentId 上级分类ID，可为 {@code null}
     * @return 最大排序值，没有则返回 {@code null}
     */
    @Nullable
    Integer maxOrder(@Param("siteId") Long siteId, @Param("parentId") @Nullable Long parentId);

    /**
     * 统计子分类数量
     *
     * @param parentId 上级分类ID
     * @return 子分类数量
     */
    int countChildren(@Param("parentId") Long parentId);

    /**
     * 统计分类下的附件数量
     *
     * @param categoryId 分类ID
     * @return 附件数量
     */
    int countAttachments(@Param("categoryId") Long categoryId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 删除某用户的全部个人分类（用户删除时调用）
     */
    int deleteByOwnerId(Long ownerId);
}
