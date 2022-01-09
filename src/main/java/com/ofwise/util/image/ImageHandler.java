package com.ofwise.util.image;

/**
 * 图片处理接口
 *
 * @author PONY
 */
public interface ImageHandler {
    /**
     * 裁剪图片
     *
     * @param src    原图片
     * @param dest   目标图片
     * @param x      x坐标
     * @param y      y坐标
     * @param width  宽度
     * @param height 高度
     * @return 是否操作图片
     */
    boolean crop(String src, String dest, int x, int y, int width, int height);

    /**
     * 缩小图片。按比例缩小。
     *
     * @param src    原图片。
     * @param dest   目标图片。
     * @param width  图片最大宽度。小于等于 0 则不限制。
     * @param height 图片最大高度。小于等于 0 则不限制。
     * @param mode   模式。
     * @return 是否操作图片
     */
    boolean resize(String src, String dest, int width, int height, ResizeMode mode);

    /**
     * 图片水印
     *
     * @param src       原图片。
     * @param dest      目标图片。
     * @param overlay   水印图片。
     * @param position  水印位置。1-9。NorthWest, North, NorthEast, West, Center, East, SouthWest, South, SouthEast。
     * @param dissolve  透明度。0-100。0: 完全透明; 100: 完全不透明。
     * @param minWidth  最小宽度。原图小于这个宽度，不加水印。
     * @param minHeight 最小高度。原图小于这个高度，不加水印。
     * @return 是否操作图片
     */
    boolean watermark(String src, String dest, String overlay, int position, int dissolve, int minWidth, int minHeight);
}
