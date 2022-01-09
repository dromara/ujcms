package com.ofwise.util.file;

import com.ofwise.util.web.PathResolver;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

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
    private String storePrefix = "/uploads";
    /**
     * 显示路径前缀
     */
    private String displayPrefix = "/uploads";

    public LocalFileHandler(PathResolver pathResolver, String storePrefix, String displayPrefix) {
        this.pathResolver = pathResolver;
        this.storePrefix = storePrefix;
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
    public void store(String filename, MultipartFile part) {
        File dest = new File(pathResolver.getRealPath(filename, storePrefix));
        File dir = dest.getParentFile();
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new RuntimeException("Cannot mkdirs: " + dir.getAbsolutePath());
            }
        }
        try {
            part.transferTo(dest);
        } catch (IOException e) {
            throw new RuntimeException("MultipartFile transfer to file exception.", e);
        }
    }

    @Override
    public void store(String filename, File file) {
        File dest = new File(pathResolver.getRealPath(filename, storePrefix));
        // 原图片不能删除
        try {
            FileUtils.copyFile(file, dest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Nullable
    public File getFile(String filename) {
        String extension = FilenameUtils.getExtension(filename);
        File tempFile = FilesEx.getTempFile(extension);
        File file = new File(pathResolver.getRealPath(filename, storePrefix));
        // 返回临时文件
        if (file.exists()) {
            try {
                FileUtils.copyFile(file, tempFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (tempFile.exists()) {
            return tempFile;
        }
        return null;
    }

    @Override
    @Nullable
    public InputStream getInputStream(String filename) {
        File file = new File(storePrefix + filename);
        if (file.exists()) {
            try {
                return new FileInputStream(file);
            } catch (FileNotFoundException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public void delete(String filename) {
        FileUtils.deleteQuietly(new File(pathResolver.getRealPath(filename, storePrefix)));
    }
}
