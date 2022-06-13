package com.ujcms.util.file;

import com.ujcms.util.web.PathResolver;
import freemarker.template.Template;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static com.ujcms.util.file.FilesEx.SLASH;
import static com.ujcms.util.file.FilesEx.normalize;

/**
 * 本地文件处理类
 *
 * @author PONY
 */
public class LocalFileHandler implements FileHandler {
    private PathResolver pathResolver;
    /**
     * 保存路径前缀。默认为Servlet根路径(如:/uploads)，使用file:则为服务器绝对路径(如:file:c:/tomcat/uploads)
     */
    private String storePrefix;
    /**
     * 显示路径前缀
     */
    private String displayPrefix;

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
    public void store(String filename, Template template, Map<String, Object> dataModel) {
        File dest = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        try {
            FileUtils.forceMkdirParent(dest);
        } catch (IOException e) {
            throw new RuntimeException("Store file exception.", e);
        }
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(dest), StandardCharsets.UTF_8))) {
            template.process(dataModel, writer);
        } catch (Exception e) {
            throw new RuntimeException("Store file exception.", e);
        }
    }

    @Override
    public void store(String filename, MultipartFile part) {
        File dest = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        try {
            FileUtils.forceMkdirParent(dest);
            part.transferTo(dest);
        } catch (IOException e) {
            throw new RuntimeException("Store file exception.", e);
        }
    }

    @Override
    public void store(String filename, RenderedImage image, String formatName) {
        File dest = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        try {
            FileUtils.forceMkdirParent(dest);
            ImageIO.write(image, formatName, dest);
        } catch (IOException e) {
            throw new RuntimeException("Store file exception.", e);
        }
    }

    @Override
    public void store(String filename, File file) {
        File dest = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        // 原图片不能删除
        try {
            FileUtils.copyFile(file, dest);
        } catch (IOException e) {
            throw new RuntimeException("Store file exception.", e);
        }
    }

    @Override
    public void store(String filename, InputStream source) {
        File dest = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        try {
            FileUtils.forceMkdirParent(dest);
            FileUtils.copyInputStreamToFile(source, dest);
        } catch (IOException e) {
            throw new RuntimeException("Store file exception.", e);
        }
    }

    @Override
    @Nullable
    public File getFile(String filename) {
        filename = normalize(filename);
        String extension = FilenameUtils.getExtension(filename);
        File file = new File(pathResolver.getRealPath(filename, storePrefix));
        // 返回临时文件
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
    public InputStream getInputStream(String filename) {
        File file = new File(pathResolver.getRealPath(normalize(filename), storePrefix));
        if (!file.exists()) {
            return null;
        }
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public void copyFile(String src, String dest) {
        File srcFile = new File(pathResolver.getRealPath(normalize(src), storePrefix));
        File destFile = new File(pathResolver.getRealPath(normalize(dest), storePrefix));
        try {
            FileUtils.copyFile(srcFile, destFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void copyDirectory(String src, String dest) {
        File srcFile = new File(pathResolver.getRealPath(normalize(src), storePrefix));
        File destFile = new File(pathResolver.getRealPath(normalize(dest), storePrefix));
        try {
            FileUtils.copyDirectory(srcFile, destFile);
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
}
