package com.ujcms.commons.file;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.DestroyMode;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.ujcms.commons.file.FtpClientProperties.ENCRYPTION_EXPLICIT;
import static com.ujcms.commons.file.FtpClientProperties.ENCRYPTION_IMPLICIT;

/**
 * @author PONY
 */
public class FtpClientFactory extends BasePooledObjectFactory<FTPClient> {
    private final FtpClientProperties properties;

    public FtpClientFactory(FtpClientProperties properties) {
        this.properties = properties;
    }

    @Override
    public FTPClient create() throws Exception {
        FTPClient ftp;
        if (properties.getEncryption() == ENCRYPTION_IMPLICIT) {
            ftp = new FTPSClient(true);
        } else if (properties.getEncryption() == ENCRYPTION_EXPLICIT) {
            ftp = new FTPSClient(false);
        } else {
            ftp = new FTPClient();
        }
        if (StringUtils.isNotBlank(properties.getEncoding())) {
            ftp.setControlEncoding(properties.getEncoding());
        } else {
            ftp.setControlEncoding(StandardCharsets.UTF_8.name());
        }
        FTPClientConfig config = new FTPClientConfig();
        ftp.configure(config);
        if (properties.getPort() != null) {
            ftp.connect(properties.getHostname(), properties.getPort());
        } else {
            ftp.connect(properties.getHostname());
        }
        ftp.login(properties.getUsername(), properties.getPassword());
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
        if (properties.isPassive()) {
            // 被动模式 Enter local passive mode
            ftp.enterLocalPassiveMode();
        } else {
            ftp.enterLocalActiveMode();
        }
        // 使用二进制传输数据
        ftp.setFileType(FTPClient.BINARY_FILE_TYPE);
        return ftp;
    }

    @Override
    public PooledObject<FTPClient> wrap(FTPClient ftp) {
        return new DefaultPooledObject<>(ftp);
    }

    @Override
    public boolean validateObject(PooledObject<FTPClient> p) {
        try {
            return p.getObject().sendNoOp();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void destroyObject(PooledObject<FTPClient> p, DestroyMode destroyMode) throws Exception {
        FTPClient ftp = p.getObject();
        try {
            if (ftp.isConnected()) {
                // check that control connection is working OK
                ftp.noop();
                ftp.logout();
            }
        } finally {
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (final IOException e) {
                    // do nothing
                }
            }
            super.destroyObject(p, destroyMode);
        }
    }
}
