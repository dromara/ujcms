package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.Link;
import com.ujcms.common.db.order.OrderEntityMapper;
import com.ujcms.common.query.QueryInfo;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 链接 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface LinkMapper extends OrderEntityMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Link bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Link bean);

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
    Link select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Link> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 删除链接组下所有链接（链接组删除级联）
     *
     * @param groupId 链接组ID
     * @return 删除条数
     */
    int deleteByGroupId(Long groupId);

    /**
     * 删除引用指定栏目的链接（栏目删除监听级联，link-groups D3：内容删除不因链接受阻）
     *
     * @param channelId 栏目ID
     * @return 删除条数
     */
    int deleteByChannelId(Long channelId);

    /**
     * 删除引用指定文章的链接（文章删除监听级联，link-groups D3）
     *
     * @param articleId 文章ID
     * @return 删除条数
     */
    int deleteByArticleId(Long articleId);

    /**
     * 删除站点链接（站点删除级联）
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteBySiteId(Long siteId);
}
