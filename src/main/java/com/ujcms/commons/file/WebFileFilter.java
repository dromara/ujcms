package com.ujcms.commons.file;

/**
 * @author PONY
 */
public interface WebFileFilter {
    /**
     * 接受哪些文件
     *
     * @param file 文件
     * @return 是否接受
     */
    boolean accept(WebFile file);
}
