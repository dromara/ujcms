package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.Friend;
import com.ujcms.common.query.QueryInfo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * @author PONY
 */
@Mapper
@Repository
public interface FriendMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Friend bean);

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
    Friend select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Friend> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据用户ID和好友用户ID获取数据（唯一键查询，不加载关联对象）
     *
     * @param userId   用户ID（行所有者）
     * @param friendId 好友用户ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Friend findByUserIdAndFriendId(@Param("userId") Long userId, @Param("friendId") Long friendId);

    /**
     * 更新状态
     *
     * @param id     主键ID
     * @param status 状态
     * @return 更新条数
     */
    int updateStatus(@Param("id") Long id, @Param("status") Short status);

    /**
     * 删除两个用户之间的好友关系（双向，一条语句）
     *
     * @param userId   用户ID
     * @param friendId 好友用户ID
     * @return 删除条数
     */
    int deletePair(@Param("userId") Long userId, @Param("friendId") Long friendId);

    /**
     * 根据用户ID删除数据
     *
     * @param userId 用户ID
     * @return 删除条数
     */
    int deleteByUserId(Long userId);

    /**
     * 根据好友用户ID删除数据
     *
     * @param friendId 好友用户ID
     * @return 删除条数
     */
    int deleteByFriendId(Long friendId);
}
