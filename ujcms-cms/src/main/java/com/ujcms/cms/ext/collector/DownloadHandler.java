package com.ujcms.cms.ext.collector;

import com.google.common.net.HttpHeaders;
import com.ujcms.common.file.FileHandler;
import com.ujcms.common.web.Uploads;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static com.ujcms.common.file.FilesEx.SLASH;

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
            String contentType = connection.getContentType();
            if (TYPE_IMAGE.equals(type)) {
                if (!Strings.CS.contains(contentType, TYPE_IMAGE)) {
                    return null;
                }
            } else if (TYPE_FILE.equals(type)) {
                if (Strings.CS.containsAny(contentType, FILE_EXCLUDE_CONTENT_TYPES)) {
                    return null;
                }
            }
            String extension = getExtension(connection, source.getPath(), contentType, type);
            File tempFile = Files.createTempFile(null, "." + extension).toFile();
            try (InputStream is = connection.getInputStream()) {
                FileUtils.copyInputStreamToFile(is, tempFile);
                // 对于图片，若扩展名仍无法确认，则通过文件内容（magic bytes）识别；
                // 若 ImageIO 仍无法识别，说明内容不是有效图片，放弃保存
                if (TYPE_IMAGE.equals(type) && !IMAGE_EXTENSIONS.contains(StringUtils.lowerCase(extension))) {
                    String fromContent = detectImageExtension(tempFile);
                    if (StringUtils.isBlank(fromContent)) {
                        return null;
                    }
                    extension = fromContent;
                }
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

    private String getExtension(HttpURLConnection connection, String path, String contentType, String type) {
        String disposition = connection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION);
        String fileName = StringUtils.substringBetween(disposition, "filename=\"", "\"");
        if (StringUtils.isBlank(fileName)) {
            fileName = StringUtils.substringAfter(disposition, "filename=");
        }
        if (StringUtils.isBlank(fileName)) {
            fileName = FilenameUtils.getName(path);
        }
        String extension = FilenameUtils.getExtension(fileName);
        // 对于图片，若 URL/Content-Disposition 中扩展名缺失或非图片扩展名，则基于 Content-Type 推断。
        if (TYPE_IMAGE.equals(type) && !IMAGE_EXTENSIONS.contains(StringUtils.lowerCase(extension))) {
            String fromMime = mimeToExtension(contentType);
            if (StringUtils.isNotBlank(fromMime)) {
                return fromMime;
            }
        }
        return extension;
    }

    @Nullable
    private static String mimeToExtension(@Nullable String contentType) {
        if (StringUtils.isBlank(contentType)) {
            return null;
        }
        String mime = StringUtils.substringBefore(contentType, ";").trim().toLowerCase();
        return MIME_EXTENSIONS.get(mime);
    }

    /**
     * 通过文件内容（magic bytes）识别图片扩展名。
     * 使用 ImageIO 的 ImageReader，可识别 JPEG/PNG/GIF/BMP/WebP 等 JDK 支持的格式。
     */
    @Nullable
    private static String detectImageExtension(File file) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            String formatName = readers.next().getFormatName();
            if (StringUtils.isBlank(formatName)) {
                return null;
            }
            String normalized = formatName.toLowerCase();
            return FORMAT_NAME_EXTENSIONS.getOrDefault(normalized, normalized);
        } catch (IOException e) {
            return null;
        }
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

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "tif", "tiff");

    private static final Map<String, String> MIME_EXTENSIONS = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/pjpeg", "jpg"),
            Map.entry("image/png", "png"),
            Map.entry("image/gif", "gif"),
            Map.entry("image/webp", "webp"),
            Map.entry("image/bmp", "bmp"),
            Map.entry("image/x-ms-bmp", "bmp"),
            Map.entry("image/svg+xml", "svg"),
            Map.entry("image/x-icon", "ico"),
            Map.entry("image/vnd.microsoft.icon", "ico"),
            Map.entry("image/tiff", "tif"));

    /**
     * ImageIO 返回的 format name 与标准扩展名的映射（如 JPEG -> jpg, TIFF -> tif）。
     * 其他格式（png/gif/bmp/webp）format name 直接等于扩展名，无需映射。
     */
    private static final Map<String, String> FORMAT_NAME_EXTENSIONS = Map.of(
            "jpeg", "jpg",
            "tiff", "tif");

}
