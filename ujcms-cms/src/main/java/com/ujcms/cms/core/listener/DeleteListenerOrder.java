package com.ujcms.cms.core.listener;

/**
 * 删除顺序接口
 *
 * @author PONY
 */
public interface DeleteListenerOrder {
    /**
     * 删除站点时，由于依赖顺序的先后，必须先执行远端的依赖。
     * 比如，栏目依赖站点，而文章又依赖栏目，则栏目的 order 应为 100，文章的 order 为 200；先删除文章，再删除栏目。
     *
     * @return 先后顺序，大的先执行。为方便调整，取值100跳，方便中间插入新的依赖。
     */
    int deleteListenerOrder();
}
