package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.FriendLinkType;
import com.ujcms.common.db.order.OrderEntityMapper;
import com.ujcms.common.query.QueryInfo;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 友情链接类型 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface FriendLinkTypeMapper extends OrderEntityMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(FriendLinkType bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(FriendLinkType bean);

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
    FriendLinkType select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<FriendLinkType> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据站点ID和别名获取列表
     *
     * @param siteId 站点ID
     * @param alias  别名
     * @return 数据列表
     */
    List<FriendLinkType> listByAlias(@Param("siteId") Long siteId, @Param("alias") String alias);

    /**
     * 检查别名是否存在
     *
     * @param alias  别名
     * @param siteId 站点ID
     * @return 数据条数
     */
    int existsByAlias(@Param("alias") String alias, @Param("siteId") Long siteId);
}
