package com.ujcms.util.file;

import freemarker.template.Template;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.DownloadObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import org.apache.commons.io.FilenameUtils;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * MinIO文件处理类
 *
 * @author PONY
 */
public class MinIoFileHandler implements FileHandler {
    private MinioClient client;
    private String bucket;
    /**
     * 保存路径前缀。
     */
    private String storePrefix;
    /**
     * 显示路径前缀
     */
    private String displayPrefix;

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
            template.process(dataModel, new OutputStreamWriter(os, StandardCharsets.UTF_8));
            String storeName = getStoreName(filename);
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(storeName)
                    .stream(new ByteArrayInputStream(os.toByteArray()), os.size(), -1).build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void store(String filename, MultipartFile multipart) {
        try (InputStream is = multipart.getInputStream()) {
            String storeName = getStoreName(filename);
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(storeName)
                    .stream(is, multipart.getSize(), -1).build());
        } catch (Exception e) {
            throw new RuntimeException(e);
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
            throw new RuntimeException(e);
        }
    }

    @Override
    public void store(String filename, InputStream source) {
        try (InputStream is = source) {
            String storeName = getStoreName(filename);
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(storeName)
                    .stream(is, -1, 10485760).build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void store(String filename, File file) {
        try {
            String storeName = getStoreName(filename);
            client.uploadObject(UploadObjectArgs.builder().bucket(bucket).object(storeName)
                    .filename(file.getAbsolutePath()).build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public File getFile(String filename) {
        try {
            String storeName = getStoreName(filename);
            String extension = FilenameUtils.getExtension(storeName);
            Path tempFile = Files.createTempFile(null, "." + extension);
            try {
                client.downloadObject(DownloadObjectArgs.builder().bucket(bucket).object(storeName)
                        .filename(tempFile.toString()).overwrite(true).build());
                return tempFile.toFile();
            } catch (ErrorResponseException e) {
                Files.deleteIfExists(tempFile);
                return null;
            } catch (Exception e) {
                Files.deleteIfExists(tempFile);
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public InputStream getInputStream(String filename) {
        try {
            String storeName = getStoreName(filename);
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(storeName).build());
        } catch (ErrorResponseException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void copyFile(String src, String dest) {
        try {
            client.copyObject(CopyObjectArgs.builder().bucket(bucket).object(getStoreName(dest))
                    .source(CopySource.builder().bucket(bucket).object(getStoreName(src)).build())
                    .build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void copyDirectory(String src, String dest) {
        // 拷贝文件夹
        copyFile(src, dest);
        // 拷贝文件夹下的文件
        String srcDir = getStoreName(src);
        int srcDirLength = srcDir.length();
        String destDir = getStoreName(dest);
        handleDirectory(srcDir, item -> {
            try {
                String srcName = item.objectName();
                String destName = destDir + srcName.substring(srcDirLength);
                client.copyObject(CopyObjectArgs.builder().bucket(bucket).object(destName)
                        .source(CopySource.builder().bucket(bucket).object(srcName).build())
                        .build());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public boolean deleteFileAndEmptyParentDir(String filename) {
        return deleteFile(filename);
    }

    @Override
    public boolean deleteFile(String filename) {
        try {
            String storeName = getStoreName(filename);
            if (!exist(storeName)) {
                return false;
            }
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(storeName).build());
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean deleteDirectory(String directory) {
        handleDirectory(getStoreName(directory), item -> {
            try {
                client.removeObject(RemoveObjectArgs.builder().bucket(bucket)
                        .object(item.objectName()).build());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        // 删除文件夹
        deleteFile(directory);
        return true;
    }

    private boolean exist(String storeName) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(storeName).build());
            return true;
        } catch (ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void handleDirectory(String directory, Consumer<Item> consumer) {
        try {
            int maxKeys = 1000;
            String prefix = directory + FilesEx.SLASH;
            String startAfter = handleDirectory(prefix, null, maxKeys, consumer);
            while (startAfter != null) {
                startAfter = handleDirectory(prefix, startAfter, maxKeys, consumer);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Nullable
    private String handleDirectory(String prefix, @Nullable String startAfter, int maxKeys, Consumer<Item> consumer)
            throws Exception {
        int count = 0;
        String objectName = null;
        Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder().bucket(bucket)
                .prefix(prefix).startAfter(startAfter).recursive(true).maxKeys(maxKeys).build());
        for (Result<Item> result : results) {
            Item item = result.get();
            objectName = item.objectName();
            consumer.accept(item);
            count += 1;
        }
        if (count < maxKeys) {
            return objectName;
        }
        return null;
    }

    private String getStoreName(String filename) {
        String storeName = storePrefix + FilesEx.normalize(filename);
        if (storeName.startsWith(FilesEx.SLASH)) {
            return storeName.substring(1);
        }
        return storeName;
    }
}
