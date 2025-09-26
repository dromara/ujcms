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
    private static final String[] DECOMPRESS_EXTENSION = { "zip", "7z" };

    // ZIP bomb protection constants
    /**
     * Maximum number of entries in an archive
     */
    private static final int MAX_ENTRIES = 100_000;

    /**
     * Maximum size of a single file in bytes (2GB)
     */
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024;

    /**
     * Maximum total uncompressed size in bytes (10GB)
     */
    private static final long MAX_TOTAL_SIZE = 10L * 1024 * 1024 * 1024;

    /**
     * Maximum compression ratio (uncompressed/compressed) to detect suspicious
     * files
     */
    private static final double MAX_COMPRESSION_RATIO = 1000.0;

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
     * 获取ZIP炸弹保护配置信息
     *
     * @return 保护配置信息
     */
    public static String getSecurityInfo() {
        return String.format(
                "ZIP Bomb Protection: Max entries=%d, Max file size=%d MB, Max total size=%d MB, Max compression ratio=%.1f",
                MAX_ENTRIES, MAX_FILE_SIZE / (1024 * 1024), MAX_TOTAL_SIZE / (1024 * 1024), MAX_COMPRESSION_RATIO);
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
     * 包含ZIP炸弹保护机制。
     *
     * @param in                压缩文件输入流
     * @param store             保存文件的方法
     * @param mkdir             创建文件夹的方法
     * @param ignoredExtensions 需要忽略的扩展名
     */
    public static void decompress(InputStream in, BiConsumer<String, ArchiveInputStream<?>> store,
            Consumer<String> mkdir, String... ignoredExtensions) {
        try (BufferedInputStream bufferedIn = new BufferedInputStream(in);
                ArchiveInputStream<?> archiveIn = new ArchiveStreamFactory().createArchiveInputStream(bufferedIn)) {

            DecompressionContext context = new DecompressionContext();
            ArchiveEntry entry;
            
            while ((entry = archiveIn.getNextEntry()) != null) {
                context.incrementEntryCount();
                validateEntryCount(context.getEntryCount());
                
                if (shouldSkipEntry(entry, archiveIn, ignoredExtensions)) {
                    continue;
                }
                
                validateEntryName(entry.getName());
                validateFileEntry(entry, context);
                processEntry(entry, archiveIn, store, mkdir);
            }

            logger.debug("Archive extraction completed safely. Entries: {}, Total size: {} bytes",
                    context.getEntryCount(), context.getTotalUncompressedSize());

        } catch (IOException | ArchiveException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 解压缩上下文，用于跟踪解压缩过程中的状态
     */
    private static class DecompressionContext {
        private int entryCount = 0;
        private long totalUncompressedSize = 0;

        public void incrementEntryCount() {
            entryCount++;
        }

        public int getEntryCount() {
            return entryCount;
        }

        public void addToTotalSize(long size) {
            totalUncompressedSize += size;
        }

        public long getTotalUncompressedSize() {
            return totalUncompressedSize;
        }
    }

    /**
     * 验证条目数量是否超过限制
     */
    private static void validateEntryCount(int entryCount) {
        if (entryCount > MAX_ENTRIES) {
            throw new SecurityException("Archive contains too many entries: " + entryCount +
                    " (maximum allowed: " + MAX_ENTRIES + ")");
        }
    }

    /**
     * 判断是否应该跳过当前条目
     */
    private static boolean shouldSkipEntry(ArchiveEntry entry, ArchiveInputStream<?> archiveIn, String... ignoredExtensions) {
        if (!archiveIn.canReadEntryData(entry)) {
            return true;
        }
        
        if (ignoredExtensions.length > 0) {
            String extension = FilenameUtils.getExtension(entry.getName());
            return StringUtils.equalsAnyIgnoreCase(extension, ignoredExtensions);
        }
        
        return false;
    }

    /**
     * 验证条目名称的安全性
     */
    private static void validateEntryName(String entryName) {
        if (entryName.contains("../") || entryName.contains("..\\")) {
            throw new IllegalArgumentException("Entry name contains illegal characters: " + entryName);
        }
        if (entryName.length() > 255) {
            throw new IllegalArgumentException("Entry name is too long: " + entryName.length());
        }
    }

    /**
     * 验证文件条目的安全性（ZIP炸弹保护）
     */
    private static void validateFileEntry(ArchiveEntry entry, DecompressionContext context) {
        if (entry.isDirectory()) {
            return;
        }

        long entrySize = entry.getSize();
        validateFileSize(entry, entrySize);
        validateCompressionRatio(entry, entrySize);
        validateTotalSize(entrySize, context);
    }

    /**
     * 验证单个文件大小
     */
    private static void validateFileSize(ArchiveEntry entry, long entrySize) {
        // Skip validation if size is unknown (-1)
        if (entrySize == -1) {
            return;
        }
        
        if (entrySize > MAX_FILE_SIZE) {
            throw new SecurityException("File too large: " + entry.getName() +
                    " (size: " + entrySize + " bytes, maximum allowed: " + MAX_FILE_SIZE + " bytes)");
        }
    }

    /**
     * 验证压缩比率
     */
    private static void validateCompressionRatio(ArchiveEntry entry, long entrySize) {
        if (entrySize <= 0) {
            return;
        }

        try {
            long compressedSize = getCompressedSize(entry);
            if (compressedSize > 0) {
                double compressionRatio = (double) entrySize / compressedSize;
                if (compressionRatio > MAX_COMPRESSION_RATIO) {
                    throw new SecurityException(
                            "Suspicious compression ratio detected for file: " + entry.getName() +
                                    " (ratio: " + String.format("%.2f", compressionRatio) +
                                    ", maximum allowed: " + MAX_COMPRESSION_RATIO + ")");
                }
            }
        } catch (Exception e) {
            logger.debug("Could not determine compression ratio for entry: {}", entry.getName());
        }
    }

    /**
     * 获取压缩后的大小
     */
    private static long getCompressedSize(ArchiveEntry entry) {
        if (entry instanceof java.util.zip.ZipEntry) {
            return ((java.util.zip.ZipEntry) entry).getCompressedSize();
        }
        return -1;
    }

    /**
     * 验证总解压大小
     */
    private static void validateTotalSize(long entrySize, DecompressionContext context) {
        // Only add to total size if we know the actual size
        if (entrySize > 0) {
            context.addToTotalSize(entrySize);
            if (context.getTotalUncompressedSize() > MAX_TOTAL_SIZE) {
                throw new SecurityException("Total uncompressed size too large: " + context.getTotalUncompressedSize() +
                        " bytes (maximum allowed: " + MAX_TOTAL_SIZE + " bytes)");
            }
        }
    }

    /**
     * 处理条目（目录或文件）
     */
    private static void processEntry(ArchiveEntry entry, ArchiveInputStream<?> archiveIn,
            BiConsumer<String, ArchiveInputStream<?>> store, Consumer<String> mkdir) {
        if (entry.isDirectory()) {
            mkdir.accept(entry.getName());
        } else {
            store.accept(entry.getName(), archiveIn);
        }
    }

    public static class FileStore implements BiConsumer<String, ArchiveInputStream<?>> {
        private final String dest;

        public FileStore(String dest) {
            this.dest = dest;
        }

        @Override
        public void accept(String entryName, ArchiveInputStream<?> archiveIn) {
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
