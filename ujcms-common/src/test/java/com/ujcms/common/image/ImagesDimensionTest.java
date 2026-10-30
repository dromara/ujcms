package com.ujcms.common.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link Images#readDimension(File)}：只读文件头取尺寸，不解码——上传接口据此在 {@code ImageIO.read} 之前拦下
 * 声称几万 × 几万像素的解压炸弹（AbstractUploadController.MAX_IMAGE_PIXELS）。
 */
class ImagesDimensionTest {
    @TempDir
    Path dir;

    @Test
    void readsTheDimensionOfARealImage() throws IOException {
        File file = dir.resolve("real.png").toFile();
        ImageIO.write(new BufferedImage(7, 3, BufferedImage.TYPE_INT_RGB), "png", file);

        Images.Dimension dimension = Images.readDimension(file);

        assertNotNull(dimension);
        assertEquals(7, dimension.width());
        assertEquals(3, dimension.height());
        assertEquals(21L, dimension.pixels());
    }

    @Test
    void readsAClaimedHugeDimensionFromTheHeaderWithoutDecoding() throws IOException {
        // 只有签名 + IHDR 的 PNG：头里声称 60000 × 60000（36 亿像素，解码需十几 GB），文件本身不到 50 字节
        File file = dir.resolve("bomb.png").toFile();
        Files.write(file.toPath(), pngHeader(60000, 60000));

        Images.Dimension dimension = Images.readDimension(file);

        assertNotNull(dimension);
        assertEquals(60000, dimension.width());
        assertEquals(60000, dimension.height());
        assertEquals(3_600_000_000L, dimension.pixels());
    }

    @Test
    void returnsNullForSomethingThatIsNotAnImage() throws IOException {
        File file = dir.resolve("text.png").toFile();
        Files.writeString(file.toPath(), "not an image");

        assertNull(Images.readDimension(file));
    }

    private static byte[] pngHeader(int width, int height) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteArrayOutputStream chunk = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(chunk);
        data.writeBytes("IHDR");
        data.writeInt(width);
        data.writeInt(height);
        // 8 位深度、真彩色、默认压缩 / 过滤、无隔行
        data.write(new byte[]{8, 2, 0, 0, 0});
        byte[] body = chunk.toByteArray();
        out.writeInt(body.length - 4);
        out.write(body);
        CRC32 crc = new CRC32();
        crc.update(body);
        out.writeInt((int) crc.getValue());
        return bytes.toByteArray();
    }
}
