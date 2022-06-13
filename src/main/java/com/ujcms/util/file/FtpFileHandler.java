package com.ujcms.util.file;

import freemarker.template.Template;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * FTP 文件处理类
 *
 * <a href="https://commons.apache.org/proper/commons-net/">commons-net</a>
 * <a href="https://commons.apache.org/proper/commons-net/examples/ftp/FTPClientExample.java">FTPClientExample.java</a>
 *
 * @author PONY
 */
public class FtpFileHandler implements FileHandler {
    private String hostname;
    @Nullable
    private Integer port;
    private String username;
    private String password;
    private String encoding;
    /**
     * 被动模式
     */
    private boolean passive;
    /**
     * 加密方式。0:不加密, 1: TLS隐式加密, 2: TLS显式加密
     */
    private int encryption;
    /**
     * 保存路径前缀。
     */
    private String storePrefix;
    /**
     * 显示路径前缀
     */
    private String displayPrefix;

    public FtpFileHandler(String hostname, @Nullable Integer port, String username, String password, String encoding,
                          boolean passive, int encryption, String storePrefix, String displayPrefix) {
        this.hostname = hostname;
        this.port = port;
        this.username = username;
        this.password = password;
        this.encoding = encoding;
        this.passive = passive;
        this.encryption = encryption;
        this.storePrefix = storePrefix;
        this.displayPrefix = displayPrefix;
    }

    @Override
    public String getDisplayPrefix() {
        return displayPrefix;
    }

    @Override
    public void store(String filename, Template template, Map<String, Object> dataModel) {
        execute(ftp -> {
            String storeName = getStoreName(filename);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            template.process(dataModel, new OutputStreamWriter(os,StandardCharsets.UTF_8));
            mkdir(ftp, FilenameUtils.getFullPath(storeName));
            ftp.storeFile(storeName, new ByteArrayInputStream(os.toByteArray()));
        });
    }

    @Override
    public void store(String filename, MultipartFile multipart) {
        execute(ftp -> {
            String storeName = getStoreName(filename);
            String fullPath = FilenameUtils.getFullPath(storeName);
            mkdir(ftp, fullPath);
            try (InputStream is = multipart.getInputStream()) {
                ftp.storeFile(storeName, is);
            }
        });
    }

    @Override
    public void store(String filename, RenderedImage image, String formatName) {
        execute(ftp -> {
            String storeName = getStoreName(filename);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(image, formatName, os);
            mkdir(ftp, FilenameUtils.getFullPath(storeName));
            ftp.storeFile(storeName, new ByteArrayInputStream(os.toByteArray()));
        });

    }

    @Override
    public void store(String filename, InputStream source) {
        execute(ftp -> {
            String storeName = getStoreName(filename);
            String fullPath = FilenameUtils.getFullPath(storeName);
            mkdir(ftp, fullPath);
            try (InputStream is = source) {
                ftp.storeFile(storeName, is);
            }
        });
    }

    @Override
    public void store(String filename, File file) {
        execute(ftp -> {
            String storeName = getStoreName(filename);
            String fullPath = FilenameUtils.getFullPath(storeName);
            mkdir(ftp, fullPath);
            try (InputStream is = FileUtils.openInputStream(file)) {
                ftp.storeFile(storeName, is);
            }
        });
    }

    @Nullable
    @Override
    public File getFile(String filename) {
        AtomicReference<File> ref = new AtomicReference<>();
        execute(ftp -> {
            String storeName = getStoreName(filename);
            String extension = FilenameUtils.getExtension(storeName);
            File tempFile = Files.createTempFile(null, "." + extension).toFile();
            try (OutputStream os = FileUtils.openOutputStream(tempFile)) {
                ftp.retrieveFile(storeName, os);
            } catch (Exception e) {
                FileUtils.deleteQuietly(tempFile);
                throw new RuntimeException(e);
            }
            ref.set(tempFile);
        });
        if (ref.get() != null && ref.get().exists()) {
            return ref.get();
        }
        return null;
    }

    @Nullable
    @Override
    public InputStream getInputStream(String filename) {
        AtomicReference<InputStream> ref = new AtomicReference<>();
        execute(ftp -> {
            String storeName = getStoreName(filename);
            ref.set(ftp.retrieveFileStream(storeName));
        });
        if (ref.get() != null) {
            return ref.get();
        }
        return null;
    }

    @Override
    public void copyFile(String src, String dest) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public void copyDirectory(String src, String dest) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public boolean deleteFileAndEmptyParentDir(String filename) {
        return deleteFile(filename);
    }

    @Override
    public boolean deleteFile(String filename) {
        AtomicReference<Boolean> ref = new AtomicReference<>();
        execute(ftp -> {
            String storeName = getStoreName(filename);
            ref.set(ftp.deleteFile(storeName));
        });
        if (ref.get() != null) {
            return ref.get();
        }
        return false;
    }

    @Override
    public boolean deleteDirectory(String directory) {
        AtomicReference<Boolean> ref = new AtomicReference<>();
        execute(ftp -> {
            String storeName = getStoreName(directory);
            FTPFile file = ftp.mlistFile(storeName);
            if (file == null) {
                ref.set(false);
                return;
            }
            if (file.isDirectory()) {
                deleteDirectory(storeName, ftp);
                ref.set(ftp.removeDirectory(storeName));
            } else {
                ref.set(ftp.deleteFile(storeName));
            }
        });
        if (ref.get() != null) {
            return ref.get();
        }
        return false;
    }

    /**
     * https://www.codejava.net/java-se/ftp/how-to-remove-a-non-empty-directory-on-a-ftp-server
     */
    private void deleteDirectory(String pathname, FTPClient ftp) throws IOException {
        for (FTPFile file : ftp.listFiles(pathname)) {
            String name = file.getName();
            if (".".equals(name) || "..".equals(name)) {
                // skip parent directory and the directory itself
                continue;
            }
            String subDir = pathname + "/" + name;
            if (file.isDirectory()) {
                deleteDirectory(subDir, ftp);
                ftp.removeDirectory(subDir);
            } else {
                ftp.deleteFile(subDir);
            }
        }
    }

    private String getStoreName(String filename) {
        return storePrefix + FilesEx.normalize(filename);
    }

    private static void mkdir(FTPClient ftp, String path) throws IOException {
        if (StringUtils.isBlank(path)) {
            return;
        }
        if (path.startsWith(FilesEx.SLASH)) {
            ftp.changeWorkingDirectory(FilesEx.SLASH);
        }
        for (String dir : StringUtils.split(path, FilesEx.SLASH)) {
            if (!ftp.changeWorkingDirectory(dir)) {
                ftp.makeDirectory(dir);
                ftp.changeWorkingDirectory(dir);
            }
        }
        ftp.changeWorkingDirectory(FilesEx.SLASH);
    }

    private void execute(Extractor consumer) {
        FTPClient ftp;
        if (encryption == ENCRYPTION_IMPLICIT) {
            ftp = new FTPSClient(true);
        } else if (encryption == ENCRYPTION_EXPLICIT) {
            ftp = new FTPSClient(false);
        } else {
            ftp = new FTPClient();
        }
        if (StringUtils.isNotBlank(encoding)) {
            ftp.setControlEncoding(encoding);
        } else {
            ftp.setControlEncoding(StandardCharsets.UTF_8.displayName());
        }
        FTPClientConfig config = new FTPClientConfig();
        ftp.configure(config);
        try {
            try {
                if (port != null) {
                    ftp.connect(hostname, port);
                } else {
                    ftp.connect(hostname);
                }
                ftp.login(username, password);
                int reply = ftp.getReplyCode();
                // 登录是否成功
                if (!FTPReply.isPositiveCompletion(reply)) {
                    ftp.disconnect();
                    throw new RuntimeException("FTP login failed.");
                }
                // 加密模式的设置
                if (ftp instanceof FTPSClient) {
                    // 设置缓冲大小 Set protection buffer size
                    ((FTPSClient) ftp).execPBSZ(0);
                    // 开启加密传输 Set data channel protection to private
                    ((FTPSClient) ftp).execPROT("P");
                }
                // 被动模式 或 主动模式
                if (passive) {
                    // 被动模式 Enter local passive mode
                    ftp.enterLocalPassiveMode();
                } else {
                    ftp.enterLocalActiveMode();
                }
                // 使用二进制传输数据
                ftp.setFileType(FTPClient.BINARY_FILE_TYPE);
                consumer.accept(ftp);
            } finally {
                if (ftp.isConnected()) {
                    ftp.disconnect();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private interface Extractor {
        /**
         * 执行方法
         *
         * @param ftp 准备好的FTP
         */
        void accept(FTPClient ftp) throws Exception;
    }

    /**
     * FTP加密：不加密
     */
    public static final int ENCRYPTION_NO = 0;
    /**
     * FTP加密：隐式加密
     */
    public static final int ENCRYPTION_IMPLICIT = 1;
    /**
     * FTP加密：显式加密
     */
    public static final int ENCRYPTION_EXPLICIT = 2;
}
