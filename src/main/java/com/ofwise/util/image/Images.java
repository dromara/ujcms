package com.ofwise.util.image;

import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * 图片工具类
 *
 * @author liufang
 */
public class Images {
    // 目前浏览器支持的图片有：jpg(image/jpeg),gif(image/gif),png(image/png),bmp(image/png),svg(image/svg+xml),webp(image/webp),ico(image/x-icon)
    // 以后有可能会支持谷歌超微型WebP图像格式，目前IE、Edge、火狐都不支持。
    // JDK支持的读取格式 ImageIO.getReaderFormatNames();
    // JDK支持的写入格式 ImageIO.getWriterFormatNames();
    // JDK8支持的格式有jpg, jpeg, png, gif, bmp, wbmp
    /**
     * 图片扩展名
     */
    static final String[] IMAGE_EXTENSIONS = new String[]{"jpeg", "jpg", "png", "gif", "bmp"};

    static final String JPEG = "jpeg";

    /**
     * 是否是图片扩展名
     */
    public static boolean isImage(String extension) {
        if (StringUtils.isBlank(extension)) {
            return false;
        }
        for (String imageExtension : IMAGE_EXTENSIONS) {
            if (StringUtils.equalsIgnoreCase(imageExtension, extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取图片格式
     * <p>
     * Windows10 JDK8 ImageReader 支持的格式有：
     * <p>
     * JPG, jpg, JPEG, jpeg, PNG, png, GIF, gif, BMP, bmp, WBMP, wbmp
     *
     * @param file 图片文件
     * @return 返回小写字母的图片格式，jpeg和jpg统一返回jpg。如果不支持该格式或文件不存在，则返回null。
     */
    @Nullable
    public static String getFormatName(File file) {
        // 文件不存在则返回 null
        if (!file.exists()) {
            return null;
        }
        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
            if (!it.hasNext()) {
                return null;
            }
            String formatName = it.next().getFormatName().toLowerCase();
            // jpeg和jpg是同一个东西。图片格式通常用作后缀名，所以返回更常用的jpg。
            if (JPEG.equals(formatName)) {
                formatName = "jpg";
            }
            return formatName;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 是否需要缩小图片
     *
     * @param width      缩小宽度
     * @param height     缩小高度
     * @param origWidth  原图宽度
     * @param origHeight 原图高度
     * @return 是否需要缩小图片
     */
    public static boolean isNeedResize(int width, int height, int origWidth, int origHeight) {
        return (width <= 0 || origWidth <= width) && (height <= 0 || origHeight <= height);
    }

    private static final int POSITION_MIN = 1;
    private static final int POSITION_MAX = 9;
    private static final int DISSOLVE_MIN = 0;
    private static final int DISSOLVE_MAX = 100;

    public static void validateWatermark(int position, int dissolve) {
        if (position < POSITION_MIN || position > POSITION_MAX) {
            throw new IllegalArgumentException("Argument position must >= 1 and <= 9");
        }
        if (dissolve < DISSOLVE_MIN || dissolve > DISSOLVE_MAX) {
            throw new IllegalArgumentException("Argument dissolve must >= 0 and <= 100");
        }
    }

    /**
     * 工具类不需要实例化
     */
    private Images() {
    }
}
