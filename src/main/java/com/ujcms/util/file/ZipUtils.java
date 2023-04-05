package com.ujcms.util.file;

import com.ujcms.util.web.UrlBuilder;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.ujcms.util.file.FilesEx.SLASH;

/**
 * Zip工具类
 *
 * @author PONY
 */
public class ZipUtils {
    /**
     * 支持解压的文件类型后缀
     */
    public static final String[] DECOMPRESS_EXTENSION = {"zip", "7z"};

    /**
     * 通过后缀判断是否支持解压
     *
     * @param filename 文件名
     * @return 是否支持解压
     */
    public static boolean decompressSupport(@Nullable String filename) {
        return StringUtils.endsWithAny(FilenameUtils.getExtension(filename), DECOMPRESS_EXTENSION);
    }

    /**
     * 压缩Zip文件
     *
     * @param dir          待压缩的文件夹
     * @param names        待压缩的文件名
     * @param out          压缩文件的输出流
     * @param isDir        判断是否文件夹的方法
     * @param listChildren 列出子文件的方法
     * @param retrieve     获取文件输入流的方法
     */
    public static void zip(String dir, String[] names, OutputStream out, Predicate<String> isDir,
                           Function<String, String[]> listChildren, BiConsumer<String, OutputStream> retrieve) {
        try (ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(out))) {
            for (String name : names) {
                addToZip(dir, name, zipOut, "", isDir, listChildren, retrieve);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 压缩Zip文件。递归方法。
     *
     * @param dir          待压缩的文件夹
     * @param name         待压缩的文件名
     * @param zipOut       压缩文件的输出流
     * @param isDir        判断是否文件夹的方法
     * @param listChildren 列出子文件的方法
     * @param retrieve     获取文件输入流的方法
     */
    private static void addToZip(String dir, String name, ZipOutputStream zipOut, String path, Predicate<String> isDir,
                                 Function<String, String[]> listChildren, BiConsumer<String, OutputStream> retrieve)
            throws IOException {
        String filename = UrlBuilder.of(dir).appendPath(name).toString();
        String entryName = UrlBuilder.of(path).appendPath(name).toString();
        if (isDir.test(filename)) {
            if (!entryName.endsWith(SLASH)) {
                entryName += SLASH;
            }
            ZipEntry zipEntry = new ZipEntry(entryName);
            zipOut.putNextEntry(zipEntry);
            zipOut.closeEntry();
            for (String childName : listChildren.apply(filename)) {
                addToZip(filename, FilenameUtils.getName(childName), zipOut, entryName, isDir, listChildren, retrieve);
            }
        } else {
            ZipEntry zipEntry = new ZipEntry(entryName);
            zipOut.putNextEntry(zipEntry);
            retrieve.accept(filename, zipOut);
        }
    }

    /**
     * 解压文件。支持多种压缩文件格式(7z, ar, arj, cpio, dump, tar, zip)，且自动识别压缩文件编码。
     *
     * @param in                压缩文件输入流
     * @param store             保存文件的方法
     * @param mkdir             创建文件夹的方法
     * @param ignoredExtensions 需要忽略的扩展名
     */
    public static void decompress(InputStream in, BiConsumer<String, ArchiveInputStream> store, Consumer<String> mkdir,
                                  String... ignoredExtensions) {
        try (ArchiveInputStream archiveIn = new ArchiveStreamFactory()
                .createArchiveInputStream(new BufferedInputStream(in))) {
            for (ArchiveEntry entry = archiveIn.getNextEntry(); entry != null; entry = archiveIn.getNextEntry()) {
                if (!archiveIn.canReadEntryData(entry)) {
                    continue;
                }
                String entryName = entry.getName();
                String extension = FilenameUtils.getExtension(entryName);
                if (StringUtils.equalsAnyIgnoreCase(extension, ignoredExtensions)) {
                    continue;
                }
                if (entryName.contains("../")) {
                    throw new IllegalArgumentException("Entry name contains illegal characters: " + entryName);
                }
                if (entryName.length() > 255) {
                    throw new IllegalArgumentException("Entry name is too long: " + entryName.length());
                }
                if (entry.isDirectory()) {
                    mkdir.accept(entryName);
                } else {
                    store.accept(entryName, archiveIn);
                }
            }
        } catch (IOException | ArchiveException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 解压zip。但无法判断zip文件的编码格式。
     *
     * @param in    zip文件输入流
     * @param store 保存文件的方法
     * @param mkdir 创建文件夹的方法
     * @deprecated
     */
    public static void unzip(InputStream in, BiConsumer<String, ZipInputStream> store, Consumer<String> mkdir) {
        // Windows压缩文件默认使用GBK编码，解压时必须指定GBK编码才能支持中文文件名和中文文件夹
        // 默认使用UTF-8编码，碰到中文文件名和中文文件夹会报错
        // try (ZipInputStream zipIn = new ZipInputStream(new BufferedInputStream(in), Charset.forName("GBK"))) {
        try (ZipInputStream zipIn = new ZipInputStream(new BufferedInputStream(in))) {
            ZipEntry zipEntry = zipIn.getNextEntry();
            while (zipEntry != null) {
                String entryName = zipEntry.getName();
                if (entryName.contains("../")) {
                    throw new IllegalArgumentException("ZipEntry name contains illegal characters: " + entryName);
                }
                if (entryName.length() > 255) {
                    throw new IllegalArgumentException("ZipEntry name is too long: " + entryName.length());
                }
                if (zipEntry.isDirectory()) {
                    mkdir.accept(entryName);
                } else {
                    store.accept(entryName, zipIn);
                }
                zipIn.closeEntry();
                zipEntry = zipIn.getNextEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
