package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.OrgTree;
import com.ujcms.cms.core.domain.SiteTree;
import com.ujcms.util.db.tree.TreeRelationMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

/**
 * 站点树形结构 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface SiteTreeMapper extends TreeRelationMapper<SiteTree> {
}