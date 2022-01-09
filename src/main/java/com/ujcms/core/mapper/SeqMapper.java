package com.ujcms.core.mapper;

import com.ujcms.core.domain.Seq;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 主键序列 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface SeqMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Seq bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Seq bean);

    /**
     * 删除数据
     *
     * @param name 主键ID
     * @return 删除条数
     */
    int delete(String name);

    /**
     * 根据主键获取数据
     *
     * @param name 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Seq select(String name);

    /**
     * 获取Seq并锁定数据
     *
     * @param name 序列名称
     * @return 序列实体
     */
    @Nullable
    Seq selectForUpdate(String name);
}