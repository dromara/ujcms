package com.ujcms.commons.file;

import com.ujcms.commons.web.UrlBuilder;
import freemarker.template.Template;
import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.Item;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.ujcms.commons.file.FilesEx.SLASH;
import static com.ujcms.commons.file.FilesEx.normalize;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * MinIO文件处理类
 *
 * @author PONY
 */
public class MinIoFileHandler implements FileHandler {
    private final MinioClient client;
    private final String bucket;
    /**
     * 保存路径前缀
     */
    private final String storePrefix;
    /**
     * 显示路径前缀
     */
    private final String displayPrefix;

    public MinIoFileHandler(String endpoint, String region, String bucket, String accessKey, String secretKey,
                            String storePrefix, String displayPrefix) {
        this.client = MinioClient.builder().endpoint(endpoint).region(region).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
        this.storePrefix = storePrefix;
        this.displayPrefix = displayPrefix;
    }

    @Override
    public String getDisplayPrefix() {
        return displayPrefix;
    }

    @Override
    public void store(String filename, Template template, Map<String, Object> dataModel) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            template.process(dataModel, new OutputStreamWriter(os, UTF_8));
            String storeName = getStoreName(filename);
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(storeName)
                    .stream(new ByteArrayInputStream(os.toByteArray()), os.size(), -1).build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void store(String filename, MultipartFile multipart) {
        try (InputStream is = multipart.getInputStream()) {
            String storeName = getStoreName(filename);
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(storeName)
                    .stream(is, multipart.getSize(), -1).build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

    }

    @Override
    public void store(String filename, RenderedImage image, String formatName) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(image, formatName, os);
            String storeName = getStoreName(filename);
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(storeName)
                    .stream(new ByteArrayInputStream(os.toByteArray()), os.size(), -1).build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void store(String filename, InputStream source) {
        try {
            String storeName = getStoreName(filename);
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(storeName)
                    .stream(source, -1, 10485760).build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

    }

    @Override
    public void store(String filename, File file) {
        try {
            String storeName = getStoreName(filename);
            client.uploadObject(UploadObjectArgs.builder().bucket(bucket).object(storeName)
                    .filename(file.getAbsolutePath()).build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void store(String filename, String text) {
        try {
            String storeName = getStoreName(filename);
            byte[] bytes = text.getBytes(UTF_8);
            ByteArrayInputStream input = new ByteArrayInputStream(bytes);
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(storeName)
                    .stream(input, bytes.length, -1).build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean mkdir(String dir) {
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(getStoreName(dir) + SLASH)
                    .stream(new ByteArrayInputStream(new byte[]{}), 0, -1).build());
            return true;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void unzip(MultipartFile zipPart, String destDir, String... ignoredExtensions) {
        try (InputStream inputStream = zipPart.getInputStream()) {
            unzip(inputStream, destDir, ignoredExtensions);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void unzip(InputStream inputStream, String destDir, String... ignoredExtensions) {
        ZipUtils.decompress(inputStream,
                (entryName, zipIn) -> store(UrlBuilder.of(destDir).appendPath(entryName).toString(), zipIn),
                entryName -> mkdir(UrlBuilder.of(destDir).appendPath(entryName).toString()),
                ignoredExtensions);
    }

    @Override
    public void zip(String dir, String[] names, OutputStream out,
                    BiPredicate<String, Long> isAddEntry, Predicate<String> isAddDirEntry) {
        ZipUtils.zip(dir, names, out, new ZipHandler() {
            @Override
            public boolean isDir(String filename) {
                return filename.endsWith(SLASH);
            }

            @Override
            public String[] listChildren(String filename) {
                return listFiles(filename).stream().map(WebFile::getOrigName).toArray(String[]::new);
            }

            @Override
            public void addDirEntry(String entryName, ZipOutputStream zipOut) throws IOException {
                if (isAddDirEntry.test(entryName)) {
                    ZipEntry zipEntry = new ZipEntry(entryName);
                    zipOut.putNextEntry(zipEntry);
                    zipOut.closeEntry();
                }
            }

            @Override
            public void addEntry(String filename, String entryName, ZipOutputStream zipOut) throws IOException {
                ObjectStat stat = getObjectStat(filename);
                long lastModified = stat.createdTime().toInstant().toEpochMilli();
                if (isAddEntry.test(entryName, lastModified)) {
                    ZipEntry zipEntry = new ZipEntry(entryName);
                    zipEntry.setLastModifiedTime(FileTime.fromMillis(lastModified));
                    zipOut.putNextEntry(zipEntry);
                    writeOutputStream(filename, zipOut);
                }
            }
        });
    }

    @Override
    public boolean rename(String filename, String to) {
        String storeName = getStoreName(filename);
        if (storeName.endsWith(SLASH)) {
            String toStoreName = UrlBuilder.of(FilenameUtils.getPath(storeName.substring(0, storeName.length() - 1)))
                    .appendPath(to).appendPath(SLASH).toString();
            handleDirectory(storeName, true, item -> {
                String objectName = item.objectName();
                String toItemObjectName = StringUtils.replaceOnce(objectName, storeName, toStoreName);
                renameFile(objectName, toItemObjectName);
            });
        } else {
            renameFile(storeName, FilenameUtils.getPath(storeName) + SLASH + to);
        }
        return true;
    }

    private void renameFile(String from, String to) {
        try {
            client.copyObject(CopyObjectArgs.builder()
                    .bucket(bucket).object(to)
                    .source(CopySource.builder().bucket(bucket).object(from).build())
                    .build());
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(from).build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void moveTo(String filename, String destDir) {
        String storeName = getStoreName(filename);
        String destStoreDir = getStoreName(destDir);
        if (storeName.endsWith(SLASH)) {
            String toStoreName = UrlBuilder.of(destStoreDir).appendPath(SLASH).toString();
            handleDirectory(storeName, true, item -> {
                String objectName = item.objectName();
                String toItemObjectName = StringUtils.replaceOnce(objectName, storeName, toStoreName);
                renameFile(objectName, toItemObjectName);
            });
        } else {
            renameFile(storeName, UrlBuilder.of(destStoreDir).appendPath(FilenameUtils.getName(storeName)).toString());
        }
    }

    @Override
    public void moveTo(String dir, String[] names, String destDir) {
        for (String name : names) {
            moveTo(UrlBuilder.of(dir).appendPath(name).toString(), destDir);
        }
    }

    @Override
    public boolean exist(String filename) {
        return findStatObject(getStoreName(filename)) != null;
    }

    @Override
    public boolean isFile(String filename) {
        return !filename.endsWith(SLASH) && findStatObject(getStoreName(filename)) != null;
    }

    @Override
    public boolean isDirectory(String filename) {
        return filename.endsWith(SLASH) && findStatObject(getStoreName(filename)) != null;
    }

    @Nullable
    @Override
    public File getFile(String filename) {
        try {
            String storeName = getStoreName(filename);
            String extension = FilenameUtils.getExtension(storeName);
            Path tempFile = Files.createTempFile(null, "." + extension);
            try {
                // minio-8.3以上版本才有overwrite方法。
                //.overwrite(true)
                client.downloadObject(DownloadObjectArgs.builder().bucket(bucket).object(storeName)
                        .filename(tempFile.toString()).build());
                return tempFile.toFile();
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (ErrorResponseException e) {
            // 文件不存在
            return null;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ObjectStat getObjectStat(String filename) {
        try {
            String storeName = getStoreName(filename);
            return client.statObject(StatObjectArgs.builder().bucket(bucket).object(storeName).build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String getObjectText(String filename) {
        String storeName = getStoreName(filename);
        try (InputStream in = client.getObject(
                GetObjectArgs.builder().bucket(bucket).object(storeName).build())) {
            return IOUtils.toString(in, UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public WebFile getWebFile(String filename) {
        ObjectStat stat = getObjectStat(filename);
        WebFile webFile = new WebFile(normalize(filename), displayPrefix, stat);
        if (webFile.isEditable()) {
            webFile.setText(getObjectText(filename));
        }
        return webFile;
    }

    @Override
    public void writeOutputStream(String filename, OutputStream out) {
        try {
            String storeName = getStoreName(filename);
            try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(storeName).build())) {
                IOUtils.copyLarge(in, out);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void copyFile(String src, String dest) {
        try {
            client.copyObject(CopyObjectArgs.builder().bucket(bucket).object(dest)
                    .source(CopySource.builder().bucket(bucket).object(src).build())
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void copyDirectory(String filename, String destDir) {
        String storeName = getStoreName(filename);
        String destStoreDir = getStoreName(destDir);
        if (storeName.endsWith(SLASH)) {
            String toStoreName = UrlBuilder.of(destStoreDir).appendPath(SLASH).toString();
            handleDirectory(storeName, true, item -> {
                String objectName = item.objectName();
                String toItemObjectName = StringUtils.replaceOnce(objectName, storeName, toStoreName);
                copyFile(objectName, toItemObjectName);
            });
        } else {
            copyFile(storeName, UrlBuilder.of(destStoreDir).appendPath(FilenameUtils.getName(storeName)).toString());
        }
    }

    @Override
    public void copy(String src, String dest) {
        String storeName = getStoreName(src);
        String destStoreName = getStoreName(dest);
        if (src.endsWith(SLASH)) {
            handleDirectory(getStoreName(src), true, item -> {
                String objectName = item.objectName();
                String toItemObjectName = StringUtils.replaceOnce(objectName, storeName, destStoreName);
                copyFile(objectName, toItemObjectName);
            });
        } else {
            copyFile(storeName, destStoreName);
        }
    }

    @Override
    public void copy(String dir, String[] names, String destDir) {
        for (String name : names) {
            copyDirectory(UrlBuilder.of(dir).appendPath(name).toString(), destDir);
        }
    }

    @Override
    public boolean deleteFileAndEmptyParentDir(String filename) {
        return deleteDirectory(filename);
    }

    @Override
    public boolean deleteFile(String filename) {
        try {
            String storeName = getStoreName(filename);
            if (findStatObject(storeName) == null) {
                return false;
            }
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(storeName).build());
            return true;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean deleteDirectory(String directory) {
        handleDirectory(getStoreName(directory), true, item -> {
            try {
                client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(item.objectName()).build());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        // 删除文件夹
        deleteFile(directory);
        return true;
    }

    @Nullable
    private ObjectStat findStatObject(String storeName) {
        try {
            return client.statObject(StatObjectArgs.builder().bucket(bucket).object(storeName).build());
        } catch (ErrorResponseException e) {
            return null;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void handleDirectory(String directory, boolean recursive, Consumer<Item> consumer) {
        try {
            int maxKeys = 1000;
            String prefix = directory.endsWith(SLASH) || directory.isEmpty() ? directory : directory + SLASH;
            String startAfter = null;
            String prevStartAfter;
            do {
                prevStartAfter = startAfter;
                startAfter = handleDirectory(prefix, startAfter, maxKeys, recursive, consumer);
            } while (startAfter != null && !startAfter.equals(prefix) && !startAfter.equals(prevStartAfter));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Nullable
    private String handleDirectory(String prefix, @Nullable String startAfter, int maxKeys, boolean recursive,
                                   Consumer<Item> consumer)
            throws ServerException, InvalidBucketNameException, InsufficientDataException, ErrorResponseException,
            IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException,
            InternalException {
        int count = 0;
        String objectName = null;
        Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder().bucket(bucket)
                .prefix(prefix).startAfter(startAfter).recursive(recursive).maxKeys(maxKeys).build());
        for (Result<Item> result : results) {
            Item item = new MinioItem(result.get());
            objectName = item.objectName();
            consumer.accept(item);
            count += 1;
        }
        if (count >= maxKeys) {
            return objectName;
        }
        return null;
    }

    private String getStoreName(String filename) {
        String storeName = UrlBuilder.of(storePrefix).appendPath(normalize(filename)).toString();
        if (storeName.startsWith(SLASH)) {
            return storeName.substring(1);
        }
        return storeName;
    }

    public List<Item> listItems(String path) {
        List<Item> list = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        handleDirectory(getStoreName(path), false, item -> {
            String objectName = item.objectName();
            if (!ids.contains(objectName) && !path.equals(objectName)) {
                list.add(item);
                ids.add(objectName);
            }
        });
        return list;
    }

    @Override
    public List<WebFile> listFiles(String path, WebFileFilter filter) {
        List<WebFile> list = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        handleDirectory(getStoreName(path), false, item -> {
            String id = item.objectName().substring(storePrefix.length());
            WebFile webFile = new WebFile(id, displayPrefix, item);
            if (filter.accept(webFile) && !ids.contains(id) && !path.equals(id)) {
                list.add(webFile);
                ids.add(id);
            }
        });
        return list;
    }
}
