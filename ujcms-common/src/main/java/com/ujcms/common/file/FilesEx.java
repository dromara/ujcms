package com.ujcms.common.file;

import com.ujcms.common.ip.IpAddresses;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jspecify.annotations.Nullable;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 文件工具类
 *
 * @author PONY
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
        return Optional.ofNullable(FilenameUtils.normalize(Strings.CS.remove(filename, DOUBLE_DOT), true)).orElse("");
    }

    private static String withSlash(String filename) {
        filename = Strings.CS.replace(filename, "\\", "/");
        if (!filename.startsWith(SLASH)) {
            filename = SLASH + filename;
        }
        if (!filename.endsWith(SLASH)) {
            filename = filename + SLASH;
        }
        return filename;
    }

    public static boolean containsDir(String filename, String... dirs) {
        if (StringUtils.isBlank(filename)) {
            return false;
        }
        filename = withSlash(filename);
        for (String dir : dirs) {
            dir = withSlash(dir);
            if (Strings.CI.contains(filename, dir)) {
                return true;
            }
        }
        return false;
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
        String random = RandomStringUtils.secure().next(8, '0', 'Z', true, true).toLowerCase();
        name.append(random);
        if (StringUtils.isNotBlank(extension)) {
            name.append(".");
            name.append(extension);
        }
        return name.toString();
    }

    /**
     * 为避免覆盖已有文件，返回一个尚不存在的文件：{@code file} 不存在时原样返回，否则在主文件名后依次追加
     * {@code (1)}、{@code (2)}……（扩展名不变），直到找到未被占用的名称。
     *
     * @param file 期望使用的文件
     * @return 当前不存在的文件
     */
    public static File getUniqueFile(final File file) {
        if (!file.exists()) {
            return file;
        }
        File dir = file.getAbsoluteFile().getParentFile();
        String name = file.getName();
        String ext = FilenameUtils.getExtension(name);
        String stem = FilenameUtils.getBaseName(name);
        // 空白扩展名（如 "a. "）视同没有扩展名，不追加
        String tail = StringUtils.isBlank(ext) ? "" : "." + ext;
        for (int index = 1; ; index += 1) {
            File candidate = new File(dir, stem + "(" + index + ")" + tail);
            if (!candidate.exists()) {
                return candidate;
            }
        }
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
        File temp = Files.createTempFile(null, null).toFile();
        if (getFileFromUrl(url, temp, contentType)) {
            return temp;
        }
        // 未下载（地址不允许、响应非 200 或类型不符）：删除空临时文件，返回 null 而不是 0 字节文件
        FileUtils.deleteQuietly(temp);
        return null;
    }

    /**
     * 远程地址是否允许抓取：仅 http/https；仅默认、80、443 端口；主机不得指向内网/保留地址（防止服务端请求伪造，
     * 解析域名全部地址、任一命中即拒绝，IPv4 与 IPv6 内网段均覆盖）。无法解析的主机名视为不允许。
     */
    static boolean isAllowedUrl(URL url) {
        String protocol = url.getProtocol();
        if (!HTTP.equals(protocol) && !HTTPS.equals(protocol)) {
            return false;
        }
        int port = url.getPort();
        if (port != -1 && port != HTTP_PORT && port != HTTPS_PORT) {
            return false;
        }
        try {
            return !IpAddresses.isInternalHost(url.getHost());
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * 还可以参考 {@link FileUtils#copyURLToFile(URL, File)} {@link FileUtils#copyURLToFile(URL, File, int, int)}
     *
     * @param url         要获取的URL地址
     * @param file        要保存的文件
     * @param contentType url 响应的 Content Type 需包含该字符串。为 null 则不限制。
     * @return 是否已下载并写入文件；地址不允许（见 {@link #isAllowedUrl}）、响应非 200 或 Content Type 不符时返回 {@code false}
     * @throws IOException IO 异常
     */
    public static boolean getFileFromUrl(URL url, File file, @Nullable String contentType) throws IOException {
        if (!isAllowedUrl(url)) {
            return false;
        }
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        // 不跟随重定向，防止跳转到内网地址；按连接设置，不改动 JVM 全局默认值
        conn.setInstanceFollowRedirects(false);
        if (conn.getResponseCode() != HttpServletResponse.SC_OK) {
            return false;
        }
        // ContentType 需包含自定字符串
        String responseType = conn.getContentType();
        if (StringUtils.isNotBlank(contentType) && (responseType == null || !responseType.contains(contentType))) {
            return false;
        }
        try (InputStream input = conn.getInputStream();
             OutputStream output = new FileOutputStream(file)) {
            IOUtils.copyLarge(input, output);
        }
        return true;
    }

    /**
     * 工具类不能实例化
     */
    private FilesEx() {
    }
}
