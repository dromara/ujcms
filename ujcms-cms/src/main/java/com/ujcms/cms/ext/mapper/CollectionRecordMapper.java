package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.CollectionRecord;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

/**
 * 采集记录 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface CollectionRecordMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(CollectionRecord bean);

    /**
     * 根据 采集ID 和 URL的MD5值 查询记录是否存在
     *
     * @param collectionId 采集ID
     * @param urlMd5       URL的MD5值
     * @return 是否存在。1:存在，0:不存在
     */
    int existsByUrl(@Param("collectionId") Long collectionId, @Param("urlMd5") String urlMd5);

    /**
     * 根据采集ID删除数据
     *
     * @param collectionId 采集ID
     * @return 被删除的数据条数
     */
    int deleteByCollectionId(Long collectionId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);
}
