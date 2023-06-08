package com.ujcms.commons.file;

import org.springframework.lang.Nullable;

/**
 * @author PONY
 */
public class FtpClientProperties {
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

    public FtpClientProperties(String hostname, @Nullable Integer port, String username, String password, String encoding,
                               boolean passive, int encryption) {
        this.hostname = hostname;
        this.port = port;
        this.username = username;
        this.password = password;
        this.encoding = encoding;
        this.passive = passive;
        this.encryption = encryption;
    }

    @Override
    public String toString() {
        return "FtpClientProperties{" +
                "hostname='" + hostname + '\'' +
                ", port=" + port +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", encoding='" + encoding + '\'' +
                ", passive=" + passive +
                ", encryption=" + encryption +
                '}';
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    @Nullable
    public Integer getPort() {
        return port;
    }

    public void setPort(@Nullable Integer port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public boolean isPassive() {
        return passive;
    }

    public void setPassive(boolean passive) {
        this.passive = passive;
    }

    public int getEncryption() {
        return encryption;
    }

    public void setEncryption(int encryption) {
        this.encryption = encryption;
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
