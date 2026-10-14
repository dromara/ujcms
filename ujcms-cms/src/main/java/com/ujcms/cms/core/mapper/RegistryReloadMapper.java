package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.RegistryReload;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 注册表重载审计 Mapper（页面构成）
 *
 * @author PONY
 */
@Mapper
@Repository
public interface RegistryReloadMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(RegistryReload bean);

    /**
     * 按加载时间倒序查询全部重载记录（调用方用 PageHelper 限制条数）
     *
     * @return 重载记录列表
     */
    List<RegistryReload> selectAllOrderByLoadedAtDesc();

    /**
     * 删除ID小于等于给定值的重载记录（保留数上限裁剪；雪花ID随时间单调）
     *
     * @param maxId 删除的ID上界（含）
     * @return 删除条数
     */
    int deleteByIdLessThanOrEqual(Long maxId);
}
