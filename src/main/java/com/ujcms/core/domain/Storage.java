package com.ujcms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ofwise.util.file.FileHandler;
import com.ofwise.util.file.LocalFileHandler;
import com.ofwise.util.web.PathResolver;
import com.ujcms.core.domain.base.StorageBase;
import org.springframework.lang.Nullable;

import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.Optional;

/**
 * 存储 实体类
 *
 * @author PONY
 */
@JsonIgnoreProperties({"site"})
public class Storage extends StorageBase implements Serializable {
    /**
     * 存储模式：本地服务器
     */
    public static final short MODE_LOCAL = 0;
    /**
     * 存储模式：FTP
     */
    public static final short MODE_FTP = 1;
    /**
     * 存储模式：MinIO
     */
    public static final short MODE_MINIO = 2;
    /**
     * 存储模式：阿里云
     */
    public static final short MODE_ALIYUN = 3;
    /**
     * 存储模式：腾讯云
     */
    public static final short MODE_QCLOUD = 4;
    /**
     * 存储模式：七牛云
     */
    public static final short MODE_QINIU = 5;

    public FileHandler getFileHandler(PathResolver pathResolver) {
        String storePrefix = Optional.ofNullable(getPath()).orElse("");
        String displayPrefix = Optional.ofNullable(getUrl()).orElse("");
        switch (getType()) {
            // case Storage.MODE_FTP:
            //     break;
            // case Storage.MODE_MINIO:
            //     break;
            default:
                return new LocalFileHandler(pathResolver, storePrefix, displayPrefix);
        }
    }

    @Nullable
    @Override
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getPath() {
        return super.getPath();
    }

    @Nullable
    @Override
    @Pattern(regexp = "^(?!.*\\.\\.).*$")
    public String getUrl() {
        return super.getUrl();
    }
}