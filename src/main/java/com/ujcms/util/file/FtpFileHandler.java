package com.ujcms.util.file;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.ujcms.util.web.UrlBuilder;
import freemarker.template.Template;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.ujcms.util.file.FilesEx.SLASH;
import static com.ujcms.util.file.FilesEx.normalize;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * FTP 文件处理类
 *
 * <a href="https://commons.apache.org/proper/commons-net/">commons-net</a>
 * <a href="https://commons.apache.org/proper/commons-net/examples/ftp/FTPClientExample.java">FTPClientExample.java</a>
 *
 * @author PONY
 */
public class FtpFileHandler implements FileHandler {
    private final FtpClientProperties properties;
    private final FtpClientFactory ftpClientFactory;
    /**
     * 保存路径前缀。
     */
    private final String storePrefix;
    /**
     * 显示路径前缀
     */
    private final String displayPrefix;

    public FtpFileHandler(String hostname, @Nullable Integer port, String username, String password, String encoding,
                          boolean passive, int encryption, String storePrefix, String displayPrefix) {
        this.properties = new FtpClientProperties(hostname, port, username, password,
                encoding, passive, encryption);
        this.ftpClientFactory = new FtpClientFactory(this.properties);
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
            template.process(dataModel, new OutputStreamWriter(os, UTF_8));
            mkdir(ftp, FilenameUtils.getPath(storeName));
            ftp.storeFile(storeName, new ByteArrayInputStream(os.toByteArray()));
            return true;
        });
    }

    @Override
    public void store(String filename, MultipartFile multipart) {
        execute(ftp -> {
            String storeName = getStoreName(filename);
            String fullPath = FilenameUtils.getPath(storeName);
            mkdir(ftp, fullPath);
            try (InputStream is = multipart.getInputStream()) {
                ftp.storeFile(storeName, is);
            }
            return true;
        });
    }

    @Override
    public void store(String filename, RenderedImage image, String formatName) {
        execute(ftp -> {
            String storeName = getStoreName(filename);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(image, formatName, os);
            mkdir(ftp, FilenameUtils.getPath(storeName));
            ftp.storeFile(storeName, new ByteArrayInputStream(os.toByteArray()));
            return true;
        });
    }

    @Override
    public void store(String filename, InputStream source) {
        execute(ftp -> {
            String storeName = getStoreName(filename);
            String fullPath = FilenameUtils.getPath(storeName);
            mkdir(ftp, fullPath);
            ftp.storeFile(storeName, source);
            return true;
        });
    }

    @Override
    public void store(String filename, File file) {
        execute(ftp -> {
            String storeName = getStoreName(filename);
            String fullPath = FilenameUtils.getPath(storeName);
            mkdir(ftp, fullPath);
            try (InputStream is = FileUtils.openInputStream(file)) {
                ftp.storeFile(storeName, is);
            }
            return true;
        });
    }

    @Override
    public void store(String filename, String text) {
        execute(ftp -> {
            try (InputStream is = new ByteArrayInputStream(text.getBytes(UTF_8))) {
                ftp.storeFile(getStoreName(filename), is);
            }
            return true;
        });
    }

    @Override
    public boolean mkdir(String dir) {
        return execute(ftp -> ftp.makeDirectory(getStoreName(dir)));
    }

    @Override
    public void unzip(MultipartFile zipPart, String destDir, String... ignoredExtensions) {
        try {
            ZipUtils.decompress(zipPart.getInputStream(),
                    (entryName, zipIn) -> store(UrlBuilder.of(destDir).appendPath(entryName).toString(), zipIn),
                    (entryName) -> mkdir(UrlBuilder.of(destDir).appendPath(entryName).toString()),
                    ignoredExtensions);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void zip(String dir, String[] names, OutputStream out) {
        ZipUtils.zip(dir, names, out,
                (filename) -> execute(ftp -> ftp.changeWorkingDirectory(getStoreName(filename))),
                (filename) -> execute(ftp ->
                        Optional.ofNullable(ftp.listNames(getStoreName(filename))).orElse(new String[]{})),
                this::writeOutputStream);
    }

    @Override
    public boolean rename(String filename, String to) {
        if (Objects.equals(filename, to)) {
            return false;
        }
        return execute(ftp -> ftp.rename(getStoreName(filename), getStoreName(to)));
    }

    @Override
    public void moveTo(String filename, String destDir) {
        String name = FilenameUtils.getName(filename);
        String to = UrlBuilder.of(destDir).appendPath(name).toString();
        rename(filename, to);
    }

    @Override
    public void moveTo(String dir, String[] names, String destDir) {
        for (String name : names) {
            String filename = UrlBuilder.of(dir).appendPath(name).toString();
            String to = UrlBuilder.of(destDir).appendPath(name).toString();
            rename(filename, to);
        }
    }

    @Override
    public boolean exist(String filename) {
        return execute(ftp -> {
            String storeName = getStoreName(filename);
            if (ftp.changeWorkingDirectory(storeName)) {
                return false;
            }
            return ftp.listFiles(storeName).length == 1;
        });
    }

    @Nullable
    @Override
    public File getFile(String filename) {
        File file = execute(ftp -> {
            String storeName = getStoreName(filename);
            String extension = FilenameUtils.getExtension(storeName);
            File tempFile = Files.createTempFile(null, "." + extension).toFile();
            try (OutputStream os = FileUtils.openOutputStream(tempFile)) {
                ftp.retrieveFile(storeName, os);
                return tempFile;
            } catch (Exception e) {
                FileUtils.deleteQuietly(tempFile);
                throw new RuntimeException(e);
            }
        });
        return file.exists() ? file : null;
    }

    @Override
    @Nullable
    public WebFile getWebFile(String filename) {
        return execute(ftp -> {
            String storeName = getStoreName(filename);
            if (ftp.changeWorkingDirectory(storeName)) {
                return Optional.of(new WebFile(normalize(filename), displayPrefix));
            }
            FTPFile[] files = ftp.listFiles(storeName);
            if (files.length <= 0) {
                return Optional.<WebFile>empty();
            }
            WebFile webFile = new WebFile(normalize(filename), displayPrefix, files[0]);
            if (webFile.isEditable()) {
                try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                    ftp.retrieveFile(storeName, os);
                    webFile.setText(new String(os.toByteArray(), UTF_8));
                }
            }
            return Optional.of(webFile);
        }).orElse(null);
    }

    @Override
    public void writeOutputStream(String filename, OutputStream out) {
        execute(ftp -> ftp.retrieveFile(getStoreName(filename), out));
    }

    private void copyFile(FTPClient ftp, String src, String dest) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ftp.retrieveFile(getStoreName(src), outputStream);
        InputStream is = new ByteArrayInputStream(outputStream.toByteArray());
        ftp.storeFile(getStoreName(dest), is);
    }

    @Override
    public void copy(String src, String dest) {
        execute(ftp -> {
            if (ftp.changeWorkingDirectory(getStoreName(src))) {
                ftp.makeDirectory(getStoreName(dest));
                for (String name : ftp.listNames()) {
                    copyDirectory(ftp, src, name, dest);
                }
            } else {
                copyFile(ftp, src, dest);
            }
            return true;
        });
    }

    private void copyDirectory(FTPClient ftp, String dir, String name, String path) throws IOException {
        String filename = UrlBuilder.of(dir).appendPath(name).toString();
        String pathname = UrlBuilder.of(path).appendPath(name).toString();
        String storeName = getStoreName(filename);
        if (ftp.changeWorkingDirectory(storeName)) {
            ftp.makeDirectory(getStoreName(pathname));
            for (String childName : ftp.listNames(storeName)) {
                copyDirectory(ftp, filename, FilenameUtils.getName(childName), pathname);
            }
        }
        FTPFile[] files = ftp.listFiles(storeName);
        if (files.length <= 0) {
            return;
        }
        copyFile(ftp, filename, pathname);
    }

    @Override
    public void copy(String dir, String[] names, String destDir) {
        execute(ftp -> {
            for (String name : names) {
                copyDirectory(ftp, dir, name, destDir);
            }
            return true;
        });
    }

    @Override
    public boolean deleteFileAndEmptyParentDir(String filename) {
        return deleteFile(filename);
    }

    @Override
    public boolean deleteFile(String filename) {
        return execute(ftp -> ftp.deleteFile(getStoreName(filename)));
    }

    @Override
    public boolean deleteDirectory(String directory) {
        return execute(ftp -> {
            String storeName = getStoreName(directory);
            if (ftp.changeWorkingDirectory(storeName)) {
                deleteDirectory(storeName, ftp);
                return ftp.removeDirectory(storeName);
            }
            FTPFile[] files = ftp.listFiles(storeName);
            if (files.length <= 0) {
                return false;
            }
            return ftp.deleteFile(storeName);
        });
    }

    @Override
    public List<WebFile> listFiles(String path, WebFileFilter filter) {
        final List<WebFile> list = new ArrayList<>();
        execute(ftp -> {
            for (FTPFile file : ftp.listFiles(getStoreName(path))) {
                String id = UrlBuilder.of(path).appendPath(file.getName()).toString();
                WebFile webFile = new WebFile(id, displayPrefix, file);
                if (filter.accept(webFile)) {
                    list.add(webFile);
                }
            }
            return true;
        });
        return list;
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
        return storePrefix + normalize(filename);
    }

    private static void mkdir(FTPClient ftp, String path) throws IOException {
        if (StringUtils.isBlank(path)) {
            return;
        }
        if (path.startsWith(SLASH)) {
            ftp.changeWorkingDirectory(SLASH);
        }
        for (String dir : StringUtils.split(path, SLASH)) {
            if (!ftp.changeWorkingDirectory(dir)) {
                ftp.makeDirectory(dir);
                ftp.changeWorkingDirectory(dir);
            }
        }
        ftp.changeWorkingDirectory(SLASH);
    }

    private <T> T execute(Extractor<T> consumer) {
        String key = properties.toString();
        FtpClientPool pool = POOL_CACHE.get(key, k -> new FtpClientPool(ftpClientFactory, new FtpClientPoolConfig()));
        if (pool == null) {
            throw new IllegalStateException("Cannot get FtpClientPool");
        }
        try {
            FTPClient ftp = pool.borrowObject();
            try {
                return consumer.accept(ftp);
            } finally {
                pool.returnObject(ftp);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private interface Extractor<T> {
        /**
         * 执行方法
         *
         * @param ftp 准备好的
         * @return 返回需要的结果，如果需要返回null，可考虑使用Optional
         * @throws Exception 抛出的异常
         */
        T accept(FTPClient ftp) throws Exception;
    }

    private static final Cache<String, FtpClientPool> POOL_CACHE = Caffeine.newBuilder()
            // 最大数量
            .maximumSize(50)
            // 设置缓存策略在1天未写入过期缓存
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .removalListener((RemovalListener<String, FtpClientPool>) (key, value, cause) -> {
                if (value != null && !value.isClosed()) {
                    value.close();
                }
            })
            .build();
}
