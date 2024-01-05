package com.ujcms.commons.file;

import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author PONY
 */
public interface ZipHandler {
    /**
     * 是否文件夹
     *
     * @param filename 文件名
     * @return 是否文件夹
     */
    boolean isDir(String filename);

    /**
     * 下级文件列表
     *
     * @param filename 当前文件名
     * @return 下级文件列表
     */
    String[] listChildren(String filename);

    /**
     * 加入文件
     *
     * @param filename  文件名
     * @param entryName entry名
     * @param zipOut    Zip输出流
     * @throws IOException IO异常
     */
    void addEntry(String filename, String entryName, ZipOutputStream zipOut) throws IOException;

    /**
     * 加入目录
     *
     * @param entryName entry名
     * @param zipOut    Zip输出流
     * @throws IOException IO异常
     */
    default void addDirEntry(String entryName, ZipOutputStream zipOut) throws IOException {
        ZipEntry zipEntry = new ZipEntry(entryName);
        zipOut.putNextEntry(zipEntry);
        zipOut.closeEntry();
    }
}
