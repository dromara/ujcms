package com.ujcms.commons.file;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.time.Duration;

/**
 * @author PONY
 */
public class FtpClientPoolConfig extends GenericObjectPoolConfig<FTPClient> {
    public FtpClientPoolConfig() {
        // 最大空闲数
        setMaxIdle(5);
        // 最小空闲数
        setMinIdle(0);
        // 最大池对象总数
        setMaxTotal(10);
        // 逐出连接的最小空闲时间。默认30分钟
        setMinEvictableIdleTime(Duration.ofMinutes(2));
        // 最大等待时间。默认为-1，表示无限等待
        setMaxWait(Duration.ofSeconds(30));
        // 在获取对象的时候检查有效性, 默认false
        setTestOnBorrow(true);
    }
}
