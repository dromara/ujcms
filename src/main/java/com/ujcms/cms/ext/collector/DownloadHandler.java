package com.ujcms.cms.ext.collector;

import com.google.common.net.HttpHeaders;
import com.ujcms.commons.file.FileHandler;
import com.ujcms.commons.web.Uploads;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

import static com.ujcms.commons.file.FilesEx.SLASH;

/**
 * 下载处理类
 *
 * @author PONY
 */
@Component
public class DownloadHandler {
    @Nullable
    public String download(String url, String type, String userAgent, FileHandler fileHandler, String basePath) {
        try {
            URL source = new URL(url);
            // file（下载）支持重定向支持，其他的不支持。
            HttpURLConnection.setFollowRedirects(TYPE_FILE.equals(type));
            HttpURLConnection connection = (HttpURLConnection) source.openConnection();
            connection.setRequestProperty("User-Agent", userAgent);
            connection.connect();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            if (TYPE_IMAGE.equals(type)) {
                String contentType = connection.getContentType();
                if (!StringUtils.contains(contentType, TYPE_IMAGE)) {
                    return null;
                }
            } else if (TYPE_FILE.equals(type)) {
                String contentType = connection.getContentType();
                if (StringUtils.containsAny(contentType, FILE_EXCLUDE_CONTENT_TYPES)) {
                    return null;
                }
            }
            String extension = getExtension(connection, source.getPath());
            File tempFile = Files.createTempFile(null, "." + extension).toFile();
            try (InputStream is = connection.getInputStream()) {
                FileUtils.copyInputStreamToFile(is, tempFile);
                String pathname = basePath + SLASH + type + Uploads.getRandomFilename(extension);
                fileHandler.store(pathname, tempFile);
                return fileHandler.getDisplayPrefix() + pathname;
            } finally {
                FileUtils.deleteQuietly(tempFile);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String getExtension(HttpURLConnection connection, String path) {
        String disposition = connection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION);
        String fileName = StringUtils.substringBetween(disposition, "filename=\"", "\"");
        if (StringUtils.isBlank(fileName)) {
            fileName = StringUtils.substringAfter(disposition, "filename=");
        }
        if (StringUtils.isBlank(fileName)) {
            fileName = FilenameUtils.getName(path);
        }
        return FilenameUtils.getExtension(fileName);
    }

    /**
     * 下载类型：图片
     */
    public static final String TYPE_IMAGE = "image";
    /**
     * 下载类型：文件
     */
    public static final String TYPE_FILE = "file";

    private static final String[] FILE_EXCLUDE_CONTENT_TYPES = {"text/html", "text/plain", "text/xml", "text/css",
            "application/xhtml+xml", "application/xml", "application/javascript", "application/json"};

}
