package com.ofwise.util.file;

import freemarker.template.Template;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
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
     * 删除文件
     *
     * @param filename 文件名
     * @return 如果文件被删除，则返回{@code true}，否则返回{@code false}
     */
    boolean delete(String filename);
}
