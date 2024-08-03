package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.Form;
import com.ujcms.commons.db.order.OrderEntityMapper;
import com.ujcms.commons.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * @author PONY
 */
@Mapper
@Repository
public interface FormMapper extends OrderEntityMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Form bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Form bean);

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
    @Override
    Form select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo  查询条件
     * @param orgIds     组织ID列表
     * @param orgRoleIds 组织权限角色ID列表
     * @param orgPermIds 组织权限组织ID列表
     * @return 数据列表
     */
    List<Form> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                         @Nullable @Param("orgIds") Collection<Long> orgIds,
                         @Nullable @Param("orgRoleIds") Collection<Long> orgRoleIds,
                         @Nullable @Param("orgPermIds") Collection<Long> orgPermIds);

    /**
     * 根据 id 列表查询
     *
     * @param ids id 列表
     * @return 数据列表
     */
    List<Form> listByIds(@Param("ids") Iterable<Long> ids);

    /**
     * 更新修改人员
     *
     * @param fromUserId 原修改用户ID
     * @param toUserId   新修改用户ID
     * @return 被更新的条数
     */
    int updateModifiedUser(@Param("fromUserId") Long fromUserId, @Param("toUserId") Long toUserId);

    /**
     * 根据 用户ID 查询数据是否存在
     *
     * @param userId 用户ID
     * @return 数据是否存在。0代表不存在，1代表存在
     */
    int existsByUserId(Long userId);

    /**
     * 根据 组织ID 查询数据是否存在
     *
     * @param orgId 组织ID
     * @return 数据是否存在。0代表不存在，1代表存在
     */
    int existsByOrgId(Long orgId);

    /**
     * 根据 类型ID 查询数据是否存在
     *
     * @param typeId 类型ID
     * @return 数据是否存在。0: 不存在, 1: 存在。
     */
    int existsByTypeId(Long typeId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);
}