package com.ujcms.common.web;

import com.ujcms.common.security.Secures;
import com.ujcms.common.web.exception.Http400Exception;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

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
     * 获得上传文件名（含按月归档的目录）。格式为 /yyyy/MM/yyyyMMddHHmmssSSS_12Random.extension
     * <p>
     * 目录只为了目录整洁（附件表存完整路径，删除按路径走），没有代码解析 yyyy/MM 段；按年/月/日归档不做配置项：
     * 切换后新旧文件散在两种布局里，谁也清不掉。
     *
     * @param extension 文件扩展名
     * @return 文件名
     */
    public static String getRandomFilename(@Nullable String extension) {
        LocalDateTime now = LocalDateTime.now();
        return DIR_FORMATTER.format(now) + randomName(now, extension);
    }

    /**
     * 获得不含目录的上传文件名。格式为 yyyyMMddHHmmssSSS_12Random.extension
     * <p>
     * 供自带目录的上传使用（如头像按 /avatar/{userId}/ 归档），与 {@link #getRandomFilename} 同一套命名：
     * 时间戳前缀使目录列表按上传时间排序，12 位安全随机数在同一毫秒内也不会重名，不含原始文件名。
     *
     * @param extension 文件扩展名
     * @return 文件名
     */
    public static String getRandomName(@Nullable String extension) {
        return randomName(LocalDateTime.now(), extension);
    }

    private static String randomName(LocalDateTime now, @Nullable String extension) {
        String name = NAME_FORMATTER.format(now) + Secures.randomLowerCaseAlphanumeric(RANDOM_LENGTH);
        if (StringUtils.isNotBlank(extension)) {
            name += "." + extension;
        }
        return name;
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

    private static final DateTimeFormatter DIR_FORMATTER = DateTimeFormatter.ofPattern("/yyyy/MM/");
    private static final DateTimeFormatter NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS_");
    private static final int RANDOM_LENGTH = 12;

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
