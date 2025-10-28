package com.ujcms.commons.web;

import com.ujcms.commons.security.Secures;
import com.ujcms.commons.web.exception.Http400Exception;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Iterator;

/**
 * 上传工具类
 *
 * @author PONY
 */
public class Uploads {
    /**
     * 使用ImageReader判断图片类型
     * <p>
     * 目前浏览器支持的图片有：jpg(image/jpeg),gif(image/gif),png(image/png),bmp(image/png),svg(image/svg+xml),webp(image/webp),ico(image/x-icon)
     * <p>
     * 以后有可能会支持谷歌超微型WebP图像格式，目前IE、Edge、火狐都不支持。
     * <p>
     * JDK支持的读取格式 ImageIO.getReaderFormatNames();
     * <p>
     * JDK支持的写入格式 ImageIO.getWriterFormatNames();
     * <p>
     * JDK8支持的格式有jpg, jpeg, png, gif, bmp, wbmp
     *
     * @param filename 图片地址
     * @return 图片类型
     */
    @Nullable
    public static String getFormatName(String filename) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new File(filename))) {
            Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
            if (it.hasNext()) {
                return it.next().getFormatName();
            }
            return null;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 获取缩略图文件名。在原文件名上加前缀 m_ ，如 /path/abc.jpg 则为 /path/m.abc.jpg
     *
     * @param filename 图片地址。
     * @return 缩略图文件名
     */
    public static String getThumbnailName(String filename) {
        if (StringUtils.isBlank(filename)) {
            return filename;
        }
        int index = filename.lastIndexOf("/");
        return filename.substring(0, index + 1) + "m_" + filename.substring(index + 1);
    }

    /**
     * 获得上传文件名。格式为 /yyyy/MM/yyyyMMddHHmmssSSS_12Random.extension
     *
     * @param extension 文件扩展名
     * @return 文件名
     */
    public static String getRandomFilename(@Nullable String extension) {
        String filename = FORMATTER.format(LocalDateTime.now()) + Secures.randomLowerCaseAlphanumeric(12);
        if (StringUtils.isNotBlank(extension)) {
            filename += "." + extension;
        }
        return filename;
    }

    public static void validateLimit(long limit, long fileSize) {
        if (limit != 0 && fileSize > limit) {
            throw new Http400Exception(String.format("file size is too large: %s, must less than %s.",
                    fileSize, limit));
        }
    }

    public static void validateType(String types, @Nullable String extension) {
        if (!isValidType(types, extension)) {
            throw new Http400Exception(String.format("file extension not allowed: '%s'. allowed extension: '%s'",
                    extension, types));
        }
    }

    public static boolean isValidType(String types, @Nullable String type) {
        return Arrays.asList(types.toLowerCase().split(",")).contains(StringUtils.lowerCase(type));
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("/yyyy/MM/yyyyMMddHHmmssSSS_");

    public static final String IMAGE_TYPE = "image";
    public static final String VIDEO_TYPE = "video";
    public static final String AUDIO_TYPE = "audio";
    public static final String DOC_TYPE = "doc";
    public static final String FILE_TYPE = "file";
    public static final String AVATAR_TYPE = "avatar";

    /**
     * 工具类不需要实例化
     */
    private Uploads() {
    }
}
