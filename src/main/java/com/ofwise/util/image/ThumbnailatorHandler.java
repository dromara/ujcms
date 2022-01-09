package com.ofwise.util.image;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Position;
import net.coobird.thumbnailator.geometry.Positions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 使用 thumbnailator 组件处理图片
 *
 * @author PONY
 */
public class ThumbnailatorHandler implements ImageHandler {
    /**
     * @see ImageHandler#crop(String, String, int, int, int, int)
     */
    @Override
    public boolean crop(String src, String dest, int x, int y, int width, int height) {
        CropParam.validate(width, height);
        try {
            BufferedImage buff = ImageIO.read(new File(src));
            CropParam param = CropParam.calculate(x, y, width, height, buff.getWidth(), buff.getHeight());
            // 宽高与原图一致，不做处理
            if (param.equalToOrig()) {
                return false;
            }
            Thumbnails.of(buff).sourceRegion(param.getX(), param.getY(), param.getWidth(), param.getHeight())
                    .scale(1).toFile(new File(dest));
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @see ImageHandler#resize(String, String, int, int, ResizeMode)
     */
    @Override
    public boolean resize(String src, String dest, int width, int height, ResizeMode mode) {
        if (width <= 0 && height <= 0) {
            return false;
        }
        try {
            BufferedImage buff = ImageIO.read(new File(src));
            int origWidth = buff.getWidth();
            int origHeight = buff.getHeight();
            if (Images.isNeedResize(width, height, origWidth, origHeight)) {
                return false;
            }
            if (width > 0 && height > 0 && mode == ResizeMode.cut) {
                // 判断以宽压缩，还是以高压缩。应该以比例更小的一端压缩。如何计算呢？
                Thumbnails.Builder<java.awt.image.BufferedImage> builder = Thumbnails.of(buff);
                float scale = (float) height / (float) width;
                float origScale = (float) origHeight / (float) origWidth;
                if (scale > origScale) {
                    int cutWidth = Math.round(origHeight / scale);
                    int x = (origWidth - cutWidth) / 2;
                    builder.sourceRegion(x, 0, cutWidth, origHeight);
                } else {
                    int cutHeight = Math.round(origWidth * scale);
                    int y = (origHeight - cutHeight) / 2;
                    builder.sourceRegion(0, y, origWidth, cutHeight);
                }
                builder.size(width, height);
                builder.toFile(dest);
            } else if (width > 0 && height > 0 && mode == ResizeMode.fix) {
                Thumbnails.of(buff).forceSize(width, height).toFile(dest);
            } else {
                Thumbnails.Builder<java.awt.image.BufferedImage> builder = Thumbnails.of(buff);
                if (width > 0) {
                    builder.width(width);
                }
                if (height > 0) {
                    builder.height(height);
                }
                builder.toFile(dest);
            }
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @see ImageHandler#watermark(String, String, String, int, int, int, int)
     */
    @Override
    public boolean watermark(String src, String dest, String overlay, int position, int dissolve, int minWidth, int minHeight) {
        Images.validateWatermark(position, dissolve);
        Position pos;
        switch (position) {
            case 1:
                pos = Positions.TOP_LEFT;
                break;
            case 2:
                pos = Positions.TOP_CENTER;
                break;
            case 3:
                pos = Positions.TOP_RIGHT;
                break;
            case 4:
                pos = Positions.CENTER_LEFT;
                break;
            case 5:
                pos = Positions.CENTER;
                break;
            case 6:
                pos = Positions.CENTER_RIGHT;
                break;
            case 7:
                pos = Positions.BOTTOM_LEFT;
                break;
            case 8:
                pos = Positions.BOTTOM_CENTER;
                break;
            default:
                pos = Positions.BOTTOM_RIGHT;
        }
        float opacity = ((float) dissolve) / 100f;
        try {
            BufferedImage buff = ImageIO.read(new File(src));
            if (buff.getWidth() < minWidth || buff.getHeight() < minHeight) {
                return false;
            }
            Thumbnails.of(buff).watermark(pos, ImageIO.read(new File(overlay)), opacity).scale(1).toFile(dest);
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
