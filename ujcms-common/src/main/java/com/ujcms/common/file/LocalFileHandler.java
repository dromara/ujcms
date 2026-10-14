package com.ujcms.common.file;

import com.ujcms.common.web.PathResolver;
import com.ujcms.common.web.UrlBuilder;
import freemarker.template.Template;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.ujcms.common.file.FilesEx.SLASH;
import static com.ujcms.common.file.FilesEx.normalize;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 本地文件处理类
 *
 * @author PONY
 */
public class LocalFileHandler implements FileHandler {
    private static final Logger logger = LoggerFactory.getLogger(LocalFileHandler.class);

    private final PathResolver pathResolver;
    /**
     * 保存路径前缀。默认为Servlet根路径(如:/uploads)，使用file:则为服务器绝对路径(如:file:c:/tomcat/uploads)
     */
    private final String storePrefix;
    /**
     * 显示路径前缀
     */
    private final String displayPrefix;

    public LocalFileHandler(PathResolver pathResolver, String storePrefix, String displayPrefix) {
        this.pathResolver = pathResolver;
        this.storePrefix = normalize(storePrefix);
        this.displayPrefix = displayPrefix;
    }

    @Override
    public String getDisplayPrefix() {
        if (storePrefix.startsWith(PathResolver.FILE_PREFIX)) {
            return displayPrefix;
        }
        return pathResolver.getContextPath() + displayPrefix;
    }

    @Override
    public void store(String filename, Template template, Map<String, Object> dataModel) {
        File dest = resolveNewFile(filename);
        try {
            FileUtils.forceMkdirParent(dest);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot mkdir parent dir.", e);
        }
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(dest), UTF_8))) {
            template.process(dataModel, writer);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (!dest.setReadable(true, false)) {
            logger.info(SET_READABLE_FAILED);
        }
    }

    @Override
    public void store(String filename, MultipartFile part) {
        File dest = resolveNewFile(filename);
        try {
            FileUtils.forceMkdirParent(dest);
            part.transferTo(dest);
            if (!dest.setReadable(true, false)) {
                logger.info(SET_READABLE_FAILED);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void store(String filename, RenderedImage image, String formatName) {
        File dest = resolveNewFile(filename);
        try {
            FileUtils.forceMkdirParent(dest);
            ImageIO.write(image, formatName, dest);
            if (!dest.setReadable(true, false)) {
                logger.info(SET_READABLE_FAILED);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void store(String filename, File file) {
        File dest = resolveNewFile(filename);
        try {
            FileUtils.copyFile(file, dest);
            if (!dest.setReadable(true, false)) {
                logger.info(SET_READABLE_FAILED);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void store(String filename, InputStream source) {
        File dest = resolveNewFile(filename);
        try {
            FileUtils.forceMkdirParent(dest);
            FileUtils.copyToFile(source, dest);
            if (!dest.setReadable(true, false)) {
                logger.info(SET_READABLE_FAILED);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void store(String filename, String text) {
        File dest = resolveNewFile(filename);
        try {
            FileUtils.forceMkdirParent(dest);
            FileUtils.write(dest, text, UTF_8);
            if (!dest.setReadable(true, false)) {
                logger.info(SET_READABLE_FAILED);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 解析待写入（新建或覆盖）文件的本地路径。
     * <p>
     * 不能直接对目标文件调用 {@code getRealPath}：Tomcat 的资源根（StandardRoot）会缓存路径查找结果，
     * 对尚不存在的路径同样记为“不存在”并保留 cacheTtl（默认 5 秒）。凡经 ServletContext 提供的静态资源
     * （上传目录、主题目录、生成的静态页等），文件写入后在这段时间内仍被判定为 404，表现为刚上传的图片、
     * 刚生成的页面短暂无法访问。目录条目的缓存不影响其下文件的查找，因此只对父目录取真实路径，再拼接文件名。
     */
    private File resolveNewFile(String filename) {
        String normalized = normalize(filename);
        int index = normalized.lastIndexOf(SLASH);
        String parent = index > 0 ? normalized.substring(0, index) : SLASH;
        String name = normalized.substring(index + 1);
        String parentPath = pathResolver.getRealPath(parent, storePrefix);
        if (parentPath == null) {
            throw new IllegalStateException("Cannot resolve real path of directory: " + parent);
        }
        return new File(parentPath, name);
    }

    @Override
    public boolean mkdir(String dir) {
        File dest = new File(pathResolver.getRealPath(normalize(dir), storePrefix));
        return dest.mkdirs();
    }

    @Override
    public void unzip(MultipartFile zipPart, String destDir, String... ignoredExtensions) {
        try (InputStream inputStream = zipPart.getInputStream()) {
            unzip(inputStream, destDir, ignoredExtensions);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void unzip(InputStream inputStream, String destDir, String... ignoredExtensions) {
        ZipUtils.decompress(inputStream,
                (entryName, zipIn) -> {
                    String filename = UrlBuilder.of(destDir).appendPath(entryName).toString();
                    // 防止写入WEB-INF目录
                    String webInf = "/WEB-INF";
                    if (FilesEx.containsDir(filename, webInf)) {
                        return;
                    }
                    store(filename, zipIn);
                },
                entryName -> mkdir(UrlBuilder.of(destDir).appendPath(entryName).toString()),
                ignoredExtensions);
    }


    @Override
    public void zip(String dir, String[] names, OutputStream out,
                    BiPredicate<String, Long> isAddEntry, Predicate<String> isAddDirEntry) {
        ZipUtils.zip(dir, names, out, new ZipHandler() {
            // getRealPath 对 Tomcat 拒绝翻译的资源返回 null（如 allowLinking=false 下的软链——
            // pnpm/npm 安装的主题 node_modules/.bin 即是）：跳过该条目并 WARN，不让整个备份 NPE 中断
            @Nullable
            private String realPath(String filename) {
                String realPath = pathResolver.getRealPath(filename, storePrefix);
                if (realPath == null) {
                    logger.warn("Zip entry skipped, real path unresolvable (symlink?): {}", filename);
                }
                return realPath;
            }

            @Override
            public boolean isDir(String filename) {
                String realPath = realPath(filename);
                return realPath != null && new File(realPath).isDirectory();
            }

            @Override
            public String[] listChildren(String filename) {
                String realPath = realPath(filename);
                if (realPath == null) {
                    return new String[]{};
                }
                return Optional.ofNullable(new File(realPath).list()).orElse(new String[]{});
            }

            @Override
            public void addDirEntry(String entryName, ZipOutputStream zipOut) throws IOException {
                if (isAddDirEntry.test(entryName)) {
                    ZipEntry zipEntry = new ZipEntry(entryName);
                    zipOut.putNextEntry(zipEntry);
                    zipOut.closeEntry();
                }
            }

            @Override
            public void addEntry(String filename, String entryName, ZipOutputStream zipOut) throws IOException {
                String realPath = realPath(normalize(filename));
                if (realPath == null) {
                    return;
                }
                File file = new File(realPath);
                if (!file.exists()) {
                    return;
                }
                long lastModified = file.lastModified();
                if (isAddEntry.test(entryName, lastModified)) {
                    ZipEntry zipEntry = new ZipEntry(entryName);
                    zipEntry.setLastModifiedTime(FileTime.fromMillis(lastModified));
                    zipOut.putNextEntry(zipEntry);
                    try (FileInputStream in = new FileInputStream(file)) {
                        IOUtils.copyLarge(in, zipOut);
                    }
                }
            }
        });
    }

    @Override
    public boolean rename(String filename, String to) {
        File file = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        return file.renameTo(new File(file.getParentFile(), to));
    }

    @Override
    public void moveTo(String filename, String destDir) {
        File file = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        File destDirFile = new File(pathResolver.getRealPath(normalize(destDir), storePrefix));
        // 同一文件夹无需移动
        if (file.getParentFile().equals(destDirFile)) {
            return;
        }
        try {
            FileUtils.moveToDirectory(file, destDirFile, true);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void moveTo(String dir, String[] names, String destDir) {
        for (String name : names) {
            String filename = UrlBuilder.of(dir).appendPath(name).toString();
            moveTo(filename, destDir);
        }
    }

    @Override
    public boolean exist(String filename) {
        File file = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        return file.exists();
    }

    @Override
    public boolean isFile(String filename) {
        File file = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        return file.isFile();
    }

    @Override
    public boolean isDirectory(String filename) {
        File file = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        return file.isDirectory();
    }

    @Override
    @Nullable
    public File getFile(String filename) {
        filename = normalize(filename);
        String extension = FilenameUtils.getExtension(filename);
        File file = new File(pathResolver.getRealPath(filename, storePrefix));
        if (!file.exists()) {
            return null;
        }
        try {
            File tempFile = Files.createTempFile(null, "." + extension).toFile();
            FileUtils.copyFile(file, tempFile);
            if (!tempFile.exists()) {
                return null;
            }
            return tempFile;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    @Nullable
    public WebFile getWebFile(String filename) {
        filename = normalize(filename);
        File file = new File(pathResolver.getRealPath(filename, storePrefix));
        if (!file.exists()) {
            return null;
        }
        WebFile webFile = new WebFile(filename, displayPrefix, file);
        if (webFile.isEditable()) {
            try {
                webFile.setText(FileUtils.readFileToString(file, UTF_8.name()));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return webFile;
    }

    @Override
    public void writeOutputStream(String filename, OutputStream out) {
        File file = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        if (!file.exists()) {
            return;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            IOUtils.copyLarge(in, out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void copy(String src, String dest) {
        File srcFile = new File(pathResolver.getRealPath(normalize(src), storePrefix));
        File destFile = resolveNewFile(dest);
        try {
            if (srcFile.isDirectory()) {
                FileUtils.copyDirectory(srcFile, destFile);
            } else if (srcFile.exists()) {
                FileUtils.copyFile(srcFile, destFile);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void copy(String dir, String[] names, String dest) {
        for (String name : names) {
            doCopy(dir, name, dest);
        }
    }

    private void doCopy(String dir, String name, String dest) {
        String filename = UrlBuilder.of(dir).appendPath(name).toString();
        File file = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        if (!file.exists()) {
            return;
        }
        String destFileName = UrlBuilder.of(dest).appendPath(name).toString();
        File destFile = new File(pathResolver.getRealPath(normalize(destFileName), storePrefix));
        try {
            if (file.isDirectory()) {
                FileUtils.copyDirectory(file, destFile);
            } else {
                FileUtils.copyFile(file, destFile);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean deleteFileAndEmptyParentDir(String filename) {
        filename = normalize(filename);
        File file = new File(pathResolver.getRealPath(filename, storePrefix));
        boolean deleted = FileUtils.deleteQuietly(file);
        if (deleted) {
            File dir = file.getParentFile();
            for (int i = 0, len = StringUtils.countMatches(filename, SLASH); i < len; i += 1) {
                if (dir == null || !dir.isDirectory() || ArrayUtils.getLength(dir.list()) > 0) {
                    break;
                }
                FileUtils.deleteQuietly(dir);
                dir = dir.getParentFile();
            }
        }
        return deleted;
    }

    @Override
    public boolean deleteFile(String filename) {
        filename = normalize(filename);
        File file = new File(pathResolver.getRealPath(filename, storePrefix));
        return FileUtils.deleteQuietly(file);
    }

    @Override
    public boolean deleteDirectory(String directory) {
        directory = normalize(directory);
        File file = new File(pathResolver.getRealPath(directory, storePrefix));
        return FileUtils.deleteQuietly(file);
    }

    @Override
    public List<WebFile> listFiles(String path, WebFileFilter filter) {
        File parent = new File(pathResolver.getRealPath(normalize(path), storePrefix));
        List<WebFile> list = new ArrayList<>();
        File[] files = parent.listFiles();
        if (files == null) {
            return list;
        }
        for (File file : files) {
            String id = UrlBuilder.of(path).appendPath(file.getName()).toString();
            WebFile webFile = new WebFile(id, displayPrefix, file);
            if (filter.accept(webFile)) {
                list.add(webFile);
            }
        }
        return list;
    }
}
