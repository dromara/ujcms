package com.ujcms.commons.file;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

/**
 * @author PONY
 */
public class FtpClientPool extends GenericObjectPool<FTPClient> {
    public FtpClientPool(PooledObjectFactory<FTPClient> factory, GenericObjectPoolConfig<FTPClient> config) {
        super(factory, config);
    }
}
