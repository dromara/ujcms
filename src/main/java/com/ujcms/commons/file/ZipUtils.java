package com.ujcms.commons.file;

import com.ujcms.commons.web.UrlBuilder;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.io.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.ujcms.commons.file.FilesEx.SLASH;

/**
 * Zip工具类
 *
 * @author PONY
 */
public class ZipUtils {
    private static final Logger logger = LoggerFactory.getLogger(ZipUtils.class);
    /**
     * 支持解压的文件类型后缀
     */
    private static final String[] DECOMPRESS_EXTENSION = {"zip", "7z"};

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
     * @param dir     待压缩的文件夹
     * @param names   待压缩的文件名
     * @param out     压缩文件的输出流
     * @param handler 压缩处理对象
     */
    public static void zip(String dir, String[] names, OutputStream out, ZipHandler handler) {
        try (ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(out))) {
            for (String name : names) {
                addToZip(dir, name, zipOut, "", handler);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 压缩Zip文件。递归方法。
     *
     * @param dir     待压缩的文件夹
     * @param name    待压缩的文件名
     * @param zipOut  压缩文件的输出流
     * @param path    zip内的路径
     * @param handler 压缩处理对象
     */
    private static void addToZip(String dir, String name, ZipOutputStream zipOut, String path, ZipHandler handler)
            throws IOException {
        String filename = UrlBuilder.of(dir).appendPath(name).toString();
        String entryName = UrlBuilder.of(path).appendPath(name).toString();
        if (handler.isDir(filename)) {
            if (!entryName.endsWith(SLASH)) {
                entryName += SLASH;
            }
            handler.addDirEntry(entryName, zipOut);
            for (String childName : handler.listChildren(filename)) {
                addToZip(filename, FilenameUtils.getName(childName), zipOut, entryName, handler);
            }
            return;
        }
        handler.addEntry(filename, entryName, zipOut);
    }

    public static void zipFile(String source, String compressed) {
        File file = new File(source);
        if (!file.exists()) {
            return;
        }
        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(compressed));
             FileInputStream fileIn = new FileInputStream(source)) {
            ZipEntry zipEntry = new ZipEntry(file.getName());
            zipOut.putNextEntry(zipEntry);
            IOUtils.copyLarge(fileIn, zipOut);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void unzipFile(String compressed) {
        File file = new File(compressed);
        if (!file.exists()) {
            return;
        }
        String dest = file.getParent();
        try (FileInputStream zipIn = new FileInputStream(file)) {
            decompress(zipIn, new FileStore(dest), new MakeDir(dest));
        } catch (IOException e) {
            throw new IllegalStateException(e);
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
        try (ArchiveInputStream archiveIn =
                     new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(in))) {
            for (ArchiveEntry entry = archiveIn.getNextEntry(); entry != null; entry = archiveIn.getNextEntry()) {
                String entryName = entry.getName();
                String extension = FilenameUtils.getExtension(entryName);
                if (!archiveIn.canReadEntryData(entry)
                        || StringUtils.equalsAnyIgnoreCase(extension, ignoredExtensions)) {
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
            throw new IllegalStateException(e);
        }
    }

    public static class FileStore implements BiConsumer<String, ArchiveInputStream> {
        private final String dest;

        public FileStore(String dest) {
            this.dest = dest;
        }

        @Override
        public void accept(String entryName, ArchiveInputStream archiveIn) {
            File destFile = new File(UrlBuilder.of(dest).appendPath(entryName).toString());
            try {
                FileUtils.copyToFile(archiveIn, destFile);
                if (!destFile.setReadable(true, false)) {
                    logger.info("Failed to setReadable: {}", destFile.getParentFile());
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public static class MakeDir implements Consumer<String> {
        private final String dest;

        public MakeDir(String dest) {
            this.dest = dest;
        }

        @Override
        public void accept(String entryName) {
            File destDir = new File(UrlBuilder.of(dest).appendPath(entryName).toString());
            if (destDir.mkdirs()) {
                logger.info("Failed to mkdirs: {}", destDir.getAbsolutePath());
            }
        }
    }

    private ZipUtils() {
        throw new IllegalStateException("Utility class");
    }
}
