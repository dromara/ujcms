package com.ujcms.commons.web;

/**
 * 定义两个常用的 @JsonView
 * <p>
 * SpringBoot配置 {@code spring.jackson.mapper.DEFAULT_VIEW_INCLUSION: true} 可以很方便的对API返回数据进行不同的序列化处理
 * <p>
 * 一般列表数据的序列化不包含大字段数据（如文章正文）以免数据量过于庞大。而单个对象的序列化则需要包含全部字段。
 *
 * @author PONY
 */
public interface Views {
    /**
     * 一般用于列表(List)数据的序列化
     */
    interface List {
    }

    /**
     * 一般用于详情数据的序列化
     */
    interface Whole extends List {
    }

}
