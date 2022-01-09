package com.ofwise.util.image;

/**
 * 裁剪参数
 *
 * @author PONY
 */
public class CropParam {
    private int x;
    private int y;
    private int width;
    private int height;
    private int origWidth;
    private int origHeight;

    public CropParam(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean equalToOrig() {
        return width == origWidth && height == origHeight;
    }

    public static void validate(int width, int height) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be > 0");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be > 0");
        }
    }

    public static CropParam calculate(int x, int y, int width, int height, int origWidth, int origHeight) {
        if (x < 0) {
            x = 0;
        }
        if (y < 0) {
            y = 0;
        }
        if (x + width > origWidth) {
            if (width > origWidth) {
                width = origWidth;
                x = 0;
            } else {
                x = origWidth - width;
            }
        }
        if (y + height > origHeight) {
            if (height > origHeight) {
                height = origHeight;
                y = 0;
            } else {
                y = origHeight - height;
            }
        }
        return new CropParam(x, y, width, height);
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

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

    public int getOrigWidth() {
        return origWidth;
    }

    public void setOrigWidth(int origWidth) {
        this.origWidth = origWidth;
    }

    public int getOrigHeight() {
        return origHeight;
    }

    public void setOrigHeight(int origHeight) {
        this.origHeight = origHeight;
    }
}
