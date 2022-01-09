package com.ujcms.core.domain;

import com.ujcms.core.domain.base.BlockBase;

import java.io.Serializable;
import java.util.Objects;

/**
 * 区块 实体类
 *
 * @author PONY
 */
public class Block extends BlockBase implements Serializable {
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Block bean = (Block) o;
        return Objects.equals(getId(), bean.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}