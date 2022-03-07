package com.ujcms.core.mapper;

import com.ujcms.core.domain.SiteCustom;
import com.ofwise.util.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 站点自定义设置 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface SiteCustomMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(SiteCustom bean);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<SiteCustom> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteBySiteId(Integer siteId);

    /**
     * 根据站点ID获取列表
     *
     * @param siteId 站点ID
     * @return 数据列表
     */
    List<SiteCustom> listBySiteId(Integer siteId);
}