package com.ofwise.util.file;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件工具类
 *
 * @author liufang
 */
public class FilesEx {
    public static final String SLASH = "/";
    /**
     * 双点 .. 代表上级文件夹
     */
    public static final String DOUBLE_DOT = "..";

    private static final long KB = 1024;
    private static final int THRESHOLD_10 = 10;
    private static final int THRESHOLD_100 = 100;
    private static final int THRESHOLD_900 = 900;
    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;

    /**
     * 正规化 文件名。并且去除可以返回上级的 `..` 字符。
     */
    public static String normalize(String filename) {
        return Optional.ofNullable(FilenameUtils.normalize(StringUtils.remove(filename, DOUBLE_DOT), true)).orElse("");
    }

    public static String getSize(@Nullable Long length) {
        if (length == null) {
            return "0 KB";
        }
        if (length <= KB) {
            return "1 KB";
        }
        long lengthKb = length / KB;
        // 小于900是为了避免出现999KB 和 0.98M 哪个大的问题
        if (lengthKb < THRESHOLD_900) {
            return lengthKb + " KB";
        }
        DecimalFormat format0 = new DecimalFormat("0");
        DecimalFormat format1 = new DecimalFormat("0.#");
        DecimalFormat format2 = new DecimalFormat("0.##");
        BigDecimal lengthMb = new BigDecimal(length).divide(new BigDecimal(KB * KB), 2, RoundingMode.HALF_DOWN);
        if (lengthMb.compareTo(new BigDecimal(THRESHOLD_10)) < 0) {
            return format2.format(lengthMb) + " MB";
        } else if (lengthMb.compareTo(new BigDecimal(THRESHOLD_100)) < 0) {
            return format1.format(lengthMb) + " MB";
        } else if (lengthMb.compareTo(new BigDecimal(THRESHOLD_900)) < 0) {
            return format0.format(lengthMb) + " MB";
        }
        BigDecimal lengthGb = new BigDecimal(length).divide(new BigDecimal(KB * KB * KB), 2, RoundingMode.HALF_DOWN);
        if (lengthGb.compareTo(new BigDecimal(THRESHOLD_10)) < 0) {
            return format2.format(lengthGb) + " GB";
        } else if (lengthGb.compareTo(new BigDecimal(THRESHOLD_100)) < 0) {
            return format1.format(lengthGb) + " GB";
        } else if (lengthGb.compareTo(new BigDecimal(THRESHOLD_900)) < 0) {
            return format0.format(lengthGb) + " GB";
        }
        BigDecimal lengthTb = new BigDecimal(length).divide(new BigDecimal(KB * KB * KB * KB), 2, RoundingMode.HALF_DOWN);
        if (lengthTb.compareTo(new BigDecimal(THRESHOLD_10)) < 0) {
            return format2.format(lengthTb) + " TB";
        } else if (lengthTb.compareTo(new BigDecimal(THRESHOLD_100)) < 0) {
            return format1.format(lengthTb) + " TB";
        } else {
            return format0.format(lengthTb) + " TB";
        }
    }

    public static String randomName(String extension) {
        StringBuilder name = new StringBuilder();
        name.append(System.currentTimeMillis());
        String random = RandomStringUtils.random(8, '0', 'Z', true, true).toLowerCase();
        name.append(random);
        if (StringUtils.isNotBlank(extension)) {
            name.append(".");
            name.append(extension);
        }
        return name.toString();
    }

    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    private static final String UID = UUID.randomUUID().toString().replace('-', '_');

    private static String getUniqueId() {
        final int limit = 2000000000;
        int current = COUNTER.getAndIncrement();
        String id = Integer.toString(current);
        if (current < limit) {
            id = ("000000000" + id).substring(id.length());
        }
        return id;
    }

    /**
     * 获取临时文件，扩展名为.tmp
     */
    public static File getTempFile() {
        return getTempFile(null);
    }

    /**
     * 获取临时文件。并创建必须的父文件夹。
     *
     * @param ext 为null则默认为.tmp；如不需要扩展名可传空串""。
     */
    public static File getTempFile(@Nullable String ext) {
        if (ext == null) {
            ext = "tmp";
        }
        String suffix = StringUtils.isNotBlank(ext) ? "." + ext : "";
        String tempFileName = UID + getUniqueId() + suffix;
        File tempFile = new File(FileUtils.getTempDirectoryPath(), tempFileName);
        File dir = tempFile.getParentFile();
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new RuntimeException("Cannot mkdirs: " + dir.getAbsolutePath());
            }
        }
        return tempFile;
    }

    /**
     * Iterates over a base name and returns the first non-existent file.<br />
     * This method extracts a file's base name, iterates over it until the first non-existent appearance with
     * <code>basename(n).ext</code>. Where n is a positive integer starting from one.
     *
     * @param file base file
     * @return first non-existent file
     */
    public static File getUniqueFile(final File file) {
        if (!file.exists()) {
            return file;
        }
        File tmpFile = new File(file.getAbsolutePath());
        File parentDir = tmpFile.getParentFile();
        int count = 1;
        String extension = FilenameUtils.getExtension(tmpFile.getName());
        String baseName = FilenameUtils.getBaseName(tmpFile.getName());
        String suffix = StringUtils.isNotBlank(extension) ? "." + extension : "";
        do {
            tmpFile = new File(parentDir, baseName + "(" + count + ")" + suffix);
            count += 1;
        } while (tmpFile.exists());
        return tmpFile;
    }

    /**
     * 还可以参考 {@link FileUtils#copyURLToFile(URL, File)} {@link FileUtils#copyURLToFile(URL, File, int, int)}
     *
     * @param url         要获取的URL地址
     * @param contentType url 响应的 Content Type 需包含该字符串。为 null 则不限制。
     * @return 通过 url 获取的文件
     * @throws IOException IO 异常
     */
    @Nullable
    public static File getFileFromUrl(URL url, String contentType) throws IOException {
        File temp = getTempFile();
        getFileFromUrl(url, temp, contentType);
        if (temp.exists()) {
            return temp;
        }
        return null;
    }

    /**
     * 还可以参考 {@link FileUtils#copyURLToFile(URL, File)} {@link FileUtils#copyURLToFile(URL, File, int, int)}
     *
     * @param url         要获取的URL地址
     * @param file        要保存的文件
     * @param contentType url 响应的 Content Type 需包含该字符串。为 null 则不限制。
     * @throws IOException IO 异常
     */
    public static void getFileFromUrl(URL url, File file, String contentType) throws IOException {
        // 只支持 http 和 https 协议
        String protocol = url.getProtocol();
        if (!HTTP.equals(protocol) && !HTTPS.equals(protocol)) {
            return;
        }
        // 只允许 默认、80、443 端口
        int port = url.getPort();
        if (port != -1 && port != HTTP_PORT && port != HTTPS_PORT) {
            return;
        }
        // 不访问本机
        String host = url.getHost();
        if (InetAddress.getByName(host).isSiteLocalAddress()) {
            return;
        }

        HttpURLConnection.setFollowRedirects(false);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (conn.getResponseCode() != HttpServletResponse.SC_OK) {
            return;
        }
        // ContentType 需包含自定字符串
        if (StringUtils.isNotBlank(contentType) && !conn.getContentType().contains(contentType)) {
            return;
        }
        try (InputStream input = conn.getInputStream();
             OutputStream output = new FileOutputStream(file)) {
            IOUtils.copyLarge(input, output);
        }
    }

    /**
     * 工具类不能实例化
     */
    private FilesEx() {
    }
}
