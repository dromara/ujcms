package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Attachment;
import com.ujcms.commons.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * 附件 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface AttachmentMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Attachment bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Attachment bean);

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
    Attachment select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Attachment> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 统计附件数量
     *
     * @param siteId  站点ID
     * @param created 创建日期
     * @return 附件数量
     */
    int countByCreated(@Param("siteId") Long siteId, @Param("created") @Nullable OffsetDateTime created);

    /**
     * 根据 url 附件对象
     *
     * @param url URL
     * @return 附件对象
     */
    @Nullable
    Attachment findByUrl(String url);

    /**
     * 更新附件是否使用字段
     *
     * @param ids 附件ID集
     * @return 更新条数
     */
    int updateUsed(@Param("ids") Set<Long> ids);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);
}