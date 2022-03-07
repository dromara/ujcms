package com.ofwise.util.image;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.im4java.core.CompositeCmd;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;
import org.im4java.core.IdentifyCmd;
import org.im4java.process.ArrayListOutputConsumer;
import org.springframework.lang.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * ImageMagick 图片处理实现
 * <p>
 * http://www.graphicsmagick.org/GraphicsMagick.html
 * http://www.imagemagick.org/script/command-line-options.php
 *
 * @author PONY
 */
public class ImageMagickHandler implements ImageHandler {
    public ImageMagickHandler() {
    }

    public ImageMagickHandler(boolean userGm) {
        this.useGm = userGm;
    }

    /**
     * @see ImageHandler#crop(String, String, int, int, int, int)
     */
    @Override
    public boolean crop(String src, String dest, int x, int y, int width, int height) {
        CropParam.validate(width, height);
        try {
            ImageInfo ii = getImageInfo(src);
            CropParam param = CropParam.calculate(x, y, width, height, ii.getWidth(), ii.getHeight());
            // 宽高与原图一致，不做处理
            if (param.equalToOrig()) {
                return false;
            }
            operate(src, dest, op -> op.crop(width, height, x, y));
            return true;
        } catch (Exception e) {
            FileUtils.deleteQuietly(new File(dest));
            throw new RuntimeException(e);
        }
    }

    private void operate(String src, String dest, Consumer<IMOperation> consumer) throws IOException, IM4JavaException, InterruptedException {
        File destFile = new File(dest);
        FileUtils.forceMkdir(destFile.getParentFile());
        IMOperation op = new IMOperation();
        op.addImage(src);
        // 去除Exif信息
        op.profile("*");
        consumer.accept(op);
        op.addImage(dest);
        ConvertCmd convertCmd = getConvertCmd();
        convertCmd.run(op);
    }

    /**
     * @see ImageHandler#resize(String, String, int, int, ResizeMode)
     */
    @Override
    public boolean resize(String src, String dest, int width, int height, ResizeMode mode) {
        if (width <= 0 && height <= 0) {
            return false;
        }
        ImageInfo ii = getImageInfo(src);
        int origWidth = ii.getWidth();
        int origHeight = ii.getHeight();
        if (Images.isNeedResize(width, height, origWidth, origHeight)) {
            return false;
        }
        try {
            // 按宽高生成图片（不按比例）：resize(width, height, '!');
            // 按比例只缩小不放大
            operate(src, dest, op -> op.resize(width, height, '>'));
            return true;
        } catch (Exception e) {
            FileUtils.deleteQuietly(new File(dest));
            throw new RuntimeException(e);
        }
    }

    /**
     * @see ImageHandler#watermark(String, String, String, int, int, int, int)
     */
    @Override
    public boolean watermark(String src, String dest, String overlay, int position, int dissolve, int minWidth, int minHeight) {
        Images.validateWatermark(position, dissolve);
        Gravity gravity = Gravity.values()[position - 1];
        ImageInfo ii = getImageInfo(src);
        if (ii.getWidth() < minWidth || ii.getHeight() < minHeight) {
            return false;
        }
        watermark(src, dest, overlay, gravity, null, null, dissolve);
        return true;
    }

    /**
     * 图片水印
     *
     * @param src      原图片
     * @param dest     目标图片
     * @param overlay  水印图片
     * @param gravity  水印位置
     * @param x        偏移位置x
     * @param y        偏移位置y
     * @param dissolve 透明度
     */
    public void watermark(String src, String dest, String overlay, @Nullable Gravity gravity,
                          @Nullable Integer x, @Nullable Integer y, @Nullable Integer dissolve) {
        try {
            File destFile = new File(dest);
            FileUtils.forceMkdir(destFile.getParentFile());
            IMOperation op = new IMOperation();
            // 水印位置。NorthWest, North, NorthEast, West, Center, East, SouthWest, South, SouthEast. 默认为左上角：NorthWest。
            if (gravity != null) {
                op.gravity(gravity.name());
            }
            if (x != null || y != null) {
                op.geometry(null, null, x, y);
            }
            // 0-100透明度。0：完全透明，100：完全不透明。默认100。
            if (dissolve != null) {
                op.dissolve(dissolve);
            }
            op.addImage(overlay);
            op.addImage(src);
            op.addImage(dest);
            CompositeCmd compositeCmd = getCompositeCmd();
            compositeCmd.run(op);
        } catch (Exception e) {
            FileUtils.deleteQuietly(new File(dest));
            throw new RuntimeException(e);
        }
    }

    /**
     * 旋转图片
     *
     * @param src    原图
     * @param dest   目标图
     * @param degree 度数
     * @return 是否操作图片
     */
    public boolean rotate(String src, String dest, @Nullable Double degree) {
        // 度数为null，不处理
        if (degree == null) {
            return false;
        }
        // 一周是360度，不超过这个范围
        degree = degree % 360;
        if (degree < 0) {
            degree = 360 + degree;
        }
        // 度数0，不旋转，不处理
        if (degree == 0) {
            return false;
        }
        try {
            Double finalDegree = degree;
            operate(src, dest, op -> op.rotate(finalDegree));
            return true;
        } catch (Exception e) {
            FileUtils.deleteQuietly(new File(dest));
            throw new RuntimeException(e);
        }
    }

    /**
     * 上下（垂直）翻转图片
     *
     * @param src  原图
     * @param dest 目标图
     */
    public void flip(String src, String dest) {
        try {
            File destFile = new File(dest);
            FileUtils.forceMkdir(destFile.getParentFile());
            IMOperation op = new IMOperation();
            op.addImage(src);
            // 上下翻转
            op.flip();
            op.addImage(dest);
            ConvertCmd convertCmd = getConvertCmd();
            convertCmd.run(op);
        } catch (Exception e) {
            FileUtils.deleteQuietly(new File(dest));
            throw new RuntimeException(e);
        }

    }

    /**
     * 左右（水平）翻转图片
     *
     * @param src  原图
     * @param dest 目标图
     */
    public void flop(String src, String dest) {
        try {
            File destFile = new File(dest);
            FileUtils.forceMkdir(destFile.getParentFile());
            IMOperation op = new IMOperation();
            op.addImage(src);
            // 左右翻转
            op.flop();
            op.addImage(dest);
            ConvertCmd convertCmd = getConvertCmd();
            convertCmd.run(op);
        } catch (Exception e) {
            FileUtils.deleteQuietly(new File(dest));
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取图片信息
     *
     * @param pathname 图片路径
     * @return 图片信息
     */
    public ImageInfo getImageInfo(String pathname) {
        IMOperation op = new IMOperation();
        // http://www.graphicsmagick.org/identify.html
        // 主要是宽高，其他的参数没有太大的作用，反而有可能因为ImageMagick升级，接口规则变化导致错误
        op.format("%w,%h,%m");
        op.addImage(pathname);
        IdentifyCmd identifyCmd = getIdentifyCmd();
        ArrayListOutputConsumer output = new ArrayListOutputConsumer();
        identifyCmd.setOutputConsumer(output);
        try {
            identifyCmd.run(op);
        } catch (IOException | InterruptedException | IM4JavaException e) {
            throw new RuntimeException(e);
        }
        ArrayList<String> cmdOutput = output.getOutput();
        if (cmdOutput.size() != 1) {
            throw new RuntimeException("Cannot get image info: " + pathname);
        }
        String line = cmdOutput.get(0);
        String[] arr = line.split(",");
        return new ImageInfo(Integer.parseInt(arr[0]), Integer.parseInt(arr[1]), arr[2]);
    }

    public ConvertCmd getConvertCmd() {
        ConvertCmd cmd = new ConvertCmd(useGm);
        if (StringUtils.isNotBlank(searchPath)) {
            cmd.setSearchPath(searchPath);
        }
        return cmd;
    }

    public CompositeCmd getCompositeCmd() {
        CompositeCmd cmd = new CompositeCmd(useGm);
        if (StringUtils.isNotBlank(searchPath)) {
            cmd.setSearchPath(searchPath);
        }
        return cmd;
    }

    public IdentifyCmd getIdentifyCmd() {
        IdentifyCmd cmd = new IdentifyCmd(useGm);
        if (StringUtils.isNotBlank(searchPath)) {
            cmd.setSearchPath(searchPath);
        }
        return cmd;
    }

    private boolean useGm = false;
    private String searchPath = null;

    public boolean isUseGm() {
        return useGm;
    }

    public void setUseGm(boolean useGm) {
        this.useGm = useGm;
    }

    public String getSearchPath() {
        return searchPath;
    }

    public void setSearchPath(String searchPath) {
        this.searchPath = searchPath;
    }

    public enum Gravity {
        /**
         * 左上
         */
        NorthWest,
        /**
         * 上
         */
        North,
        /**
         * 右上
         */
        NorthEast,
        /**
         * 左
         */
        West,
        /**
         * 中
         */
        Center,
        /**
         * 右
         */
        East,
        /**
         * 左下
         */
        SouthWest,
        /**
         * 下
         */
        South,
        /**
         * 右下
         */
        SouthEast
    }

    public static class ImageInfo {
        public ImageInfo(int width, int height, String formatType) {
            this.width = width;
            this.height = height;
            this.formatType = formatType;
        }

        private int width;
        private int height;
        private String formatType;

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public String getFormatType() {
            return formatType;
        }

        public void setFormatType(String formatType) {
            this.formatType = formatType;
        }
    }
}
