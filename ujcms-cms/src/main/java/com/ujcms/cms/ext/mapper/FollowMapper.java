package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.Follow;
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
public interface FollowMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Follow bean);

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
    Follow select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Follow> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据文章ID和用户ID获取数据
     *
     * @param articleId 文章ID
     * @param userId    用户ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Follow findByArticleIdAndUserId(@Param("articleId") Long articleId, @Param("userId") Long userId);

    /**
     * 根据栏目ID和用户ID获取数据
     *
     * @param channelId 栏目ID
     * @param userId    用户ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Follow findByChannelIdAndUserId(@Param("channelId") Long channelId, @Param("userId") Long userId);

    /**
     * 根据被关注用户ID和用户ID获取数据（用户关注 type=3）
     *
     * @param toUserId 被关注用户ID
     * @param userId   用户ID（粉丝）
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Follow findByToUserIdAndUserId(@Param("toUserId") Long toUserId, @Param("userId") Long userId);

    /**
     * 获取关注文章的用户ID列表。按用户ID升序作键集分页游标，行数限制由调用方通过分页插件实现
     *
     * @param articleId     文章ID
     * @param excludeUserId 排除的用户ID（评论作者）
     * @param minUserId     用户ID下界（不含）。首次传 {@code 0}，后续传上一批最后一个用户ID
     * @return 用户ID列表
     */
    List<Long> listUserIdByArticleId(@Param("articleId") Long articleId,
                                     @Param("excludeUserId") Long excludeUserId,
                                     @Param("minUserId") Long minUserId);

    /**
     * 获取关注栏目的用户ID列表。按用户ID升序作键集分页游标，行数限制由调用方通过分页插件实现
     *
     * @param channelId     栏目ID
     * @param excludeUserId 排除的用户ID（文章作者）
     * @param minUserId     用户ID下界（不含）。首次传 {@code 0}，后续传上一批最后一个用户ID
     * @return 用户ID列表
     */
    List<Long> listUserIdByChannelId(@Param("channelId") Long channelId,
                                     @Param("excludeUserId") Long excludeUserId,
                                     @Param("minUserId") Long minUserId);

    /**
     * 获取关注某用户的粉丝用户ID列表。按用户ID升序作键集分页游标，行数限制由调用方通过分页插件实现
     *
     * @param toUserId      被关注用户ID（文章作者）
     * @param excludeUserId 排除的用户ID（作者本人）
     * @param minUserId     用户ID下界（不含）。首次传 {@code 0}，后续传上一批最后一个用户ID
     * @return 用户ID列表
     */
    List<Long> listUserIdByToUserId(@Param("toUserId") Long toUserId,
                                    @Param("excludeUserId") Long excludeUserId,
                                    @Param("minUserId") Long minUserId);

    /**
     * 统计当前用户按类型的关注数量。用户关注数（关注数）用 {@code type=3}
     *
     * @param userId 用户ID
     * @param type   类型
     * @return 关注数量
     */
    long countByUserIdAndType(@Param("userId") Long userId, @Param("type") Short type);

    /**
     * 统计某用户的粉丝数量
     *
     * @param toUserId 被关注用户ID
     * @return 粉丝数量
     */
    long countByToUserId(@Param("toUserId") Long toUserId);

    /**
     * 根据文章ID删除数据
     *
     * @param articleId 文章ID
     * @return 删除条数
     */
    int deleteByArticleId(Long articleId);

    /**
     * 根据栏目ID删除数据
     *
     * @param channelId 栏目ID
     * @return 删除条数
     */
    int deleteByChannelId(Long channelId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 根据用户ID删除数据（删除该用户发起的关注，即"我关注的"）
     *
     * @param userId 用户ID
     * @return 删除条数
     */
    int deleteByUserId(Long userId);

    /**
     * 根据被关注用户ID删除数据（删除关注该用户的行，即"关注我的"）
     *
     * @param toUserId 被关注用户ID
     * @return 删除条数
     */
    int deleteByToUserId(Long toUserId);
}
