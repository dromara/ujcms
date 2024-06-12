package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Action;
import com.ujcms.commons.query.QueryInfo;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 动作 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ActionMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Action bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Action bean);

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
    Action select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Action> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 统计投票数量，用于判断是否已经投过票。
     *
     * @param refType   引用类型
     * @param refId     引用ID
     * @param refOption 引用选项
     * @param date      此日期之后
     * @param userId    用户ID
     * @param ip        IP地址
     * @param cookie    cookie标识
     * @return 数据数量
     */
    int existsBy(@Nullable @Param("refType") String refType,
                @Nullable @Param("refId") Long refId,
                @Nullable @Param("refOption") String refOption,
                @Nullable @Param("date") OffsetDateTime date,
                @Nullable @Param("userId") Long userId,
                @Nullable @Param("ip") String ip,
                @Nullable @Param("cookie") Long cookie);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 根据用户ID删除数据
     *
     * @param userId 用户ID
     * @return 被删除的数据条数
     */
    int deleteByUserId(Long userId);
}