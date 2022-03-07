package com.ujcms.core.mapper;

import com.ujcms.core.domain.AttachmentRefer;
import com.ofwise.util.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 附件引用 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface AttachmentReferMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(AttachmentRefer bean);

    /**
     * 根据主键获取数据
     *
     * @param attachmentId 附件ID
     * @param referType    引用类型
     * @param referId      引用ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    AttachmentRefer select(@Param("attachmentId") Integer attachmentId, @Param("referType") String referType, @Param("referId") Integer referId);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<AttachmentRefer> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据附件ID获取列表
     *
     * @param attachmentId 附件ID
     * @return 数据列表
     */
    List<AttachmentRefer> listByAttachmentId(Integer attachmentId);

    /**
     * 根据引用类型和引用ID删除数据
     *
     * @param referType 引用类型
     * @param referId   引用ID
     * @return 删除条数
     */
    int deleteByReferTypeAndReferId(@Param("referType") String referType, @Param("referId") Integer referId);

    /**
     * 根据引用类型和引用ID获取数据列表
     *
     * @param referType 引用类型
     * @param referId   引用ID
     * @return 数据列表
     */
    List<AttachmentRefer> listByReferTypeAndReferId(@Param("referType") String referType, @Param("referId") Integer referId);
}