package com.ujcms.commons.file;

import freemarker.template.Template;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.RenderedImage;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * 文件处理接口
 *
 * @author PONY
 */
public interface FileHandler {
    String SET_READABLE_FAILED = "set file readable failed";

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
     * @param source   输入流。调用者需要自己负责关闭流
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
     * 存储文件
     *
     * @param filename 文件名
     * @param text     正文
     */
    void store(String filename, String text);

    /**
     * 创建文件夹
     *
     * @param dir 文件夹
     * @return 是否成功
     */
    boolean mkdir(String dir);

    /**
     * 解压文件
     *
     * @param zipPart           zip multipart 文件
     * @param destDir           解压目录
     * @param ignoredExtensions 需要忽略的扩展名
     */
    void unzip(MultipartFile zipPart, String destDir, String... ignoredExtensions);

    /**
     * 解压文件
     *
     * @param inputStream       zip 文件输入流
     * @param destDir           解压目录
     * @param ignoredExtensions 需要忽略的扩展名
     */
    void unzip(InputStream inputStream, String destDir, String... ignoredExtensions);

    /**
     * 压缩文件
     *
     * @param dir   需要压缩的文件夹
     * @param names 需压缩的文件名
     * @param out   压缩后的zip输出流
     */
    default void zip(String dir, String[] names, OutputStream out) {
        zip(dir, names, out, (entryName, lastModified) -> true, entryName -> true);
    }

    /**
     * 压缩文件
     *
     * @param dir           需要压缩的文件夹
     * @param names         需压缩的文件名
     * @param out           压缩后的zip输出流
     * @param isAddEntry    文件是否加入压缩包
     * @param isAddDirEntry 文件夹是否加入压缩包
     */
    void zip(String dir, String[] names, OutputStream out,
             BiPredicate<String, Long> isAddEntry, Predicate<String> isAddDirEntry);

    /**
     * 重命名
     *
     * @param filename 源文件名
     * @param to       目标文件
     * @return 是否成功
     */
    boolean rename(String filename, String to);

    /**
     * 移动文件
     *
     * @param filename 文件名
     * @param destDir  目标文件夹
     */
    void moveTo(String filename, String destDir);

    /**
     * 移动文件
     *
     * @param dir     待移动文件夹
     * @param names   待移动文件
     * @param destDir 目标文件夹
     */
    void moveTo(String dir, String[] names, String destDir);

    /**
     * 文件是否存在。包括文件和文件夹
     *
     * @param filename 文件名
     * @return 是否存在
     */
    boolean exist(String filename);

    /**
     * 是否文件
     *
     * @param filename 文件名
     * @return 是否文件
     */
    boolean isFile(String filename);

    /**
     * 是否文件夹
     *
     * @param filename 文件名
     * @return 是否文件夹
     */
    boolean isDirectory(String filename);

    /**
     * 获取本地文件
     *
     * @param filename 文件名
     * @return 本地文件。文件不存在时，返回null。
     */
    @Nullable
    File getFile(String filename);

    /**
     * 获取WebFile
     *
     * @param filename 文件名
     * @return 本地文件。文件不存在时，返回null。
     */
    @Nullable
    WebFile getWebFile(String filename);

    /**
     * 写入输出流
     *
     * @param filename 文件名
     * @param out      输入流
     */
    void writeOutputStream(String filename, OutputStream out);

    /**
     * 复制。可以复制文件，也可以文件夹
     *
     * @param src  源文件
     * @param dest 目标文件
     */
    void copy(String src, String dest);

    /**
     * 复制
     *
     * @param dir     待复制文件夹
     * @param names   待复制文件
     * @param destDir 目标文件夹
     */
    void copy(String dir, String[] names, String destDir);

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

    /**
     * 列出文件列表
     *
     * @param path 路径
     * @return 文件列表
     */
    default List<WebFile> listFiles(String path) {
        return listFiles(path, file -> true);
    }

    /**
     * 搜索文件列表
     *
     * @param path   路径
     * @param search 搜索关键字
     * @return 文件列表
     */
    default List<WebFile> listFiles(String path, String search) {
        return listFiles(path, new SearchWebFileFilter(search));
    }

    /**
     * 列出文件列表
     *
     * @param path   路径
     * @param filter 过滤器
     * @return 文件列表
     */
    List<WebFile> listFiles(String path, WebFileFilter filter);
}
