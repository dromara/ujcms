package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.GlobalBase;

import java.io.Serializable;

/**
 * 全局对象 实体类
 * <p>
 * 主要用于存放程序运行时需要保存的数据，而用于存放用户设置的数据。比如：上一次更新时间等。
 *
 * @author PONY
 */
public class Global extends GlobalBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public Global() {
    }

    public Global(String name, String value) {
        setName(name);
        setValue(value);
    }
}