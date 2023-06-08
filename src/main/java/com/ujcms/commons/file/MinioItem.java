package com.ujcms.commons.file;

import io.minio.messages.Item;
import io.minio.messages.Owner;
import org.springframework.lang.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.time.ZonedDateTime;
import java.util.Map;

import static com.ujcms.commons.file.FilesEx.SLASH;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 修复了 MinIO item 几个问题
 * <li>objectName没有进行URLDecoder</li>
 * <li>文件夹在获取lastModified时，会报空指针</li>
 * <li>isDir的判断不明确，现改为通过objectName是否以斜杠'/'结尾</li>
 *
 * @author PONY
 */
public class MinioItem extends Item {
    private final Item item;

    public MinioItem(Item item) {
        this.item = item;
    }

    @Override
    public String objectName() {
        try {
            return URLDecoder.decode(item.objectName(), UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Nullable
    public ZonedDateTime lastModified() {
        if (isDir()) {
            return null;
        }
        return item.lastModified();
    }

    @Override
    public String etag() {
        return item.etag();
    }

    @Override
    public long size() {
        return item.size();
    }

    @Override
    public String storageClass() {
        return item.storageClass();
    }

    @Override
    public Owner owner() {
        return item.owner();
    }

    @Override
    public Map<String, String> userMetadata() {
        return item.userMetadata();
    }

    @Override
    public boolean isLatest() {
        return item.isLatest();
    }

    @Override
    public String versionId() {
        return item.versionId();
    }

    @Override
    public boolean isDir() {
        return objectName().endsWith(SLASH);
    }

    @Override
    public boolean isDeleteMarker() {
        return item.isDeleteMarker();
    }
}
