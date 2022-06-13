package com.ujcms.util.file;

import freemarker.template.Template;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.RenderedImage;
import java.io.File;
import java.io.InputStream;
import java.util.Map;

/**
 * 文件处理接口
 *
 * @author PONY
 */
public interface FileHandler {
    /**
     * 根据 URL 地址获取文件名。
     *
     * @param url 文件的 URL 地址
     * @return 去除前缀文件名
     */
    @Nullable
    default String getName(@Nullable String url) {
        if (url != null && url.startsWith(getDisplayPrefix())) {
            return url.substring(getDisplayPrefix().length());
        }
        return null;
    }

    /**
     * 获取前缀
     *
     * @return 前缀
     */
    String getDisplayPrefix();

    /**
     * 存储文件
     *
     * @param filename  文件名
     * @param template  模板
     * @param dataModel 模板数据
     */
    void store(String filename, Template template, Map<String, Object> dataModel);

    /**
     * 存储文件
     *
     * @param filename  文件名
     * @param multipart 上传文件
     */
    void store(String filename, MultipartFile multipart);

    /**
     * 存储图片文件
     *
     * @param filename   文件名
     * @param image      图片
     * @param formatName 图片格式
     */
    void store(String filename, RenderedImage image, String formatName);

    /**
     * 存储文件
     *
     * @param filename 文件名
     * @param source   输入流
     */
    void store(String filename, InputStream source);

    /**
     * 存储文件
     *
     * @param filename 文件名
     * @param file     本地文件
     */
    void store(String filename, File file);

    /**
     * 获取本地文件
     *
     * @param filename 文件名
     * @return 本地文件。文件不存在时，返回null。
     */
    @Nullable
    File getFile(String filename);

    /**
     * 获取文件输入流
     *
     * @param filename 文件名
     * @return 输入流
     */
    @Nullable
    InputStream getInputStream(String filename);

    /**
     * 复制文件
     *
     * @param src  源文件
     * @param dest 目标文件
     */
    void copyFile(String src, String dest);

    /**
     * 复制文件夹
     *
     * @param src  源文件夹
     * @param dest 目标文件夹
     */
    void copyDirectory(String src, String dest);

    /**
     * 删除文件及上级空文件夹
     *
     * @param filename 文件名
     * @return 是否删除成功
     */
    boolean deleteFileAndEmptyParentDir(String filename);

    /**
     * 删除文件
     *
     * @param filename 文件名
     * @return 是否删除成功
     */
    boolean deleteFile(String filename);

    /**
     * 删除文件夹
     *
     * @param directory 文件夹
     * @return 是否删除成功
     */
    boolean deleteDirectory(String directory);
}
