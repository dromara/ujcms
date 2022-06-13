package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.ChannelTree;
import com.ujcms.cms.core.domain.OrgTree;
import com.ujcms.util.db.tree.TreeRelationMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

/**
 * 栏目属性结构 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ChannelTreeMapper extends TreeRelationMapper<ChannelTree> {

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Integer siteId);
}