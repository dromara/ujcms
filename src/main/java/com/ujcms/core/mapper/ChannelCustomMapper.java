package com.ujcms.core.mapper;

import com.ujcms.core.domain.ArticleCustom;
import com.ujcms.core.domain.ChannelCustom;
import com.ofwise.util.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 栏目自定义数据 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ChannelCustomMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(ChannelCustom bean);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<ChannelCustom> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);
    /**
     * 根据栏目ID删除数据
     *
     * @param channelId 文章ID
     * @return 删除条数
     */
    int deleteByChannelId(Integer channelId);

    /**
     * 根据栏目ID获取列表
     *
     * @param channelId 栏目ID
     * @return 数据列表
     */
    List<ArticleCustom> listByChannelId(Integer channelId);
}