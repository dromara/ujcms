package com.ujcms.util.file;

import com.ujcms.util.web.PathResolver;
import com.ujcms.util.web.UrlBuilder;
import freemarker.template.Template;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.util.file.FilesEx.SLASH;
import static com.ujcms.util.file.FilesEx.normalize;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 本地文件处理类
 *
 * @author PONY
 */
public class LocalFileHandler implements FileHandler {
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
        this.displayPrefix = normalize(displayPrefix);
    }

    @Override
    public String getDisplayPrefix() {
        if (storePrefix.startsWith(PathResolver.FILE_PREFIX)) {
            return displayPrefix;
        }
        return pathResolver.getContextPath() + displayPrefix;
    }

    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void store(String filename, Template template, Map<String, Object> dataModel) {
        File dest = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        try {
            FileUtils.forceMkdirParent(dest);
        } catch (IOException e) {
            throw new RuntimeException("Cannot mkdir parent dir.", e);
        }
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(dest), UTF_8))) {
            template.process(dataModel, writer);
        } catch (Exception e) {
            throw new RuntimeException("Store file exception.", e);
        }
        dest.setReadable(true, false);
    }

    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void store(String filename, MultipartFile part) {
        File dest = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        try {
            FileUtils.forceMkdirParent(dest);
            part.transferTo(dest);
            dest.setReadable(true, false);
        } catch (IOException e) {
            throw new RuntimeException("Store file exception.", e);
        }
    }

    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void store(String filename, RenderedImage image, String formatName) {
        File dest = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        try {
            FileUtils.forceMkdirParent(dest);
            ImageIO.write(image, formatName, dest);
            dest.setReadable(true, false);
        } catch (IOException e) {
            throw new RuntimeException("Store file exception.", e);
        }
    }

    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void store(String filename, File file) {
        File dest = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        try {
            FileUtils.copyFile(file, dest);
            dest.setReadable(true, false);
        } catch (IOException e) {
            throw new RuntimeException("Store file exception.", e);
        }
    }

    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void store(String filename, InputStream source) {
        File dest = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        try {
            FileUtils.forceMkdirParent(dest);
            FileUtils.copyInputStreamToFile(source, dest);
            dest.setReadable(true, false);
        } catch (IOException e) {
            throw new RuntimeException("Store file exception.", e);
        }
    }

    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void store(String filename, String text) {
        File dest = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        try {
            FileUtils.forceMkdirParent(dest);
            FileUtils.write(dest, text, UTF_8);
            dest.setReadable(true, false);
        } catch (IOException e) {
            throw new RuntimeException("Store file exception.", e);
        }
    }

    @Override
    public boolean mkdir(String dir) {
        File dest = new File(pathResolver.getRealPath(normalize(dir), storePrefix));
        return dest.mkdirs();
    }

    @Override
    public void unzip(MultipartFile zipPart, String destDir, String... ignoredExtensions) {
        try {
            ZipUtils.decompress(zipPart.getInputStream(),
                    (entryName, zipIn) -> {
                        String filename = UrlBuilder.of(destDir).appendPath(entryName).toString();
                        // 防止写入WEB-INF目录
                        String webInf = "/WEB-INF";
                        if (filename.equalsIgnoreCase(webInf)
                                || StringUtils.startsWithIgnoreCase(filename, webInf + SLASH)) {
                            return;
                        }
                        store(filename, zipIn);
                    },
                    (entryName) -> mkdir(UrlBuilder.of(destDir).appendPath(entryName).toString()),
                    ignoredExtensions);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void zip(String dir, String[] names, OutputStream out) {
        ZipUtils.zip(dir, names, out,
                (filename) -> new File(pathResolver.getRealPath(filename, storePrefix)).isDirectory(),
                (filename) -> Optional.ofNullable(new File(pathResolver.getRealPath(filename, storePrefix)).list())
                        .orElse(new String[]{}),
                this::writeOutputStream);
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
            throw new RuntimeException("Move directory error.", e);
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
        return file.exists() && !file.isDirectory();
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
            throw new RuntimeException(e);
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
                throw new RuntimeException("Read file error!", e);
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
            throw new RuntimeException(e);
        }
    }

    @Override
    public void copy(String src, String dest) {
        File srcFile = new File(pathResolver.getRealPath(normalize(src), storePrefix));
        File destFile = new File(pathResolver.getRealPath(normalize(dest), storePrefix));
        try {
            if (srcFile.isDirectory()) {
                FileUtils.copyDirectory(srcFile, destFile);
            } else {
                FileUtils.copyFile(srcFile, destFile);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
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
            throw new RuntimeException(e);
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
