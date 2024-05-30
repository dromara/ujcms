package com.ujcms.commons.misc;

import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.ScreenExtractor;
import ws.schild.jave.info.MultimediaInfo;

import java.io.File;

/**
 * 媒体工具类
 * <p>
 * JAVE: https://github.com/a-schild/jave2
 * 抓取图片: https://github.com/a-schild/jave2/blob/master/jave-core-test/src/test/java/ws/schild/jave/ScreenExtractorTest.java
 *
 * @author PONY
 */
public class MediaUtils {
    public static long getDuration(MultimediaObject multimediaObject) throws EncoderException {
        MultimediaInfo infos = multimediaObject.getInfo();
        return infos.getDuration();
    }

    /**
     * 截取一张图片。获取视频第 1/4 处的视频截图，最大为 20 秒。
     *
     * @param multimediaObject 多媒体对象
     * @param duration         视频时长
     * @param outputFile       输出的图片文件
     * @throws EncoderException Encoder异常
     */
    public static void renderOneImage(MultimediaObject multimediaObject, long duration, File outputFile) throws EncoderException {
        // 最大20秒
        long max = 20 * 1000L;
        // 加 1 防止为 0
        long millis = (duration / 4) + 1;
        if (millis > max) {
            millis = max;
        }
        if (millis > duration) {
            millis = duration;
        }
        ScreenExtractor instance = new ScreenExtractor();
        instance.renderOneImage(multimediaObject, -1, -1, millis, outputFile, 1, true);
    }

    private MediaUtils() {
    }
}
