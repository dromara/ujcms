package com.ujcms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ujcms.core.domain.base.StorageBase;

import java.io.Serializable;

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
}