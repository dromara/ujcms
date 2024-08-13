package com.ujcms.cms.core.mapper;

import com.ujcms.commons.db.tree.TreeRelationMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

/**
 * 栏目属性结构 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ChannelTreeMapper extends TreeRelationMapper {
}