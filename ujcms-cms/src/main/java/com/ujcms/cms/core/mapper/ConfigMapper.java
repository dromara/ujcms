package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Config;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 全局配置 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ConfigMapper {
    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Config bean);

    /**
     * 更新端口
     *
     * @param port 端口。可以为 null
     * @return 更新条数
     */
    int updatePort(@Nullable @Param("port") Integer port);

    /**
     * 获取唯一数据。全局表只能且必须查找唯一一条 {@code ID} 为 {@code 1} 的数据。
     *
     * @return 唯一的 Config 对象
     */
    @Nullable
    Config findUnique();
}