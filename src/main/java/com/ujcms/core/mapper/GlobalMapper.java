package com.ujcms.core.mapper;

import com.ujcms.core.domain.Global;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 全局 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface GlobalMapper {
    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Global bean);

    /**
     * 获取唯一数据。全局表只能且必须查找唯一一条 {@code ID} 为 {@code 1} 的数据。
     *
     * @return 唯一的 Global 对象
     */
    @Nullable
    Global findUnique();
}