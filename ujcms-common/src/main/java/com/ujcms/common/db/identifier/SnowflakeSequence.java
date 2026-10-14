package com.ujcms.common.db.identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 雪花算法ID
 *
 * <p>位分配（共 63 位，最高位为符号位恒为 0）：42 位时间戳 | 5 位数据中心ID | 5 位工作机器ID | 11 位序列号。</p>
 * <ul>
 * <li>时间戳 42 位（毫秒）：自 2020-10-01 起约 139 年，可用至 2160-02。</li>
 * <li>数据中心ID、工作机器ID 各 5 位：取值范围 0-31，共 1024 个节点组合。</li>
 * <li>序列号 11 位：每毫秒最多 2048 个 ID（新毫秒从 1 或 2 随机起始，实际 2046 或 2047 个），每秒约 205 万个。</li>
 * </ul>
 *
 * <p>时钟回拨采用逻辑时钟处理：时间戳取「当前时间」与「上次时间戳」的较大者，序列号用尽时借用下一毫秒，
 * 既不等待也不抛异常，单进程内 ID 严格递增。代价是回拨期间 ID 中的时间戳略超前于系统时间；
 * 若进程在超前期间重启，理论上存在与重启前 ID 重复的可能。回拨幅度达到 1 秒时记录警告日志，
 * 持续回拨期间幅度每再扩大 1 秒记录一次，系统时间追上逻辑时钟后记录一条恢复日志。</p>
 *
 * <p>未显式配置节点ID时，根据网卡 MAC 地址、主机名、进程ID 自动推算。容器/K8s 环境中 MAC 可能完全相同
 * （如 Calico 固定为 ee:ee:ee:ee:ee:ee）、进程ID 恒为 1，仅靠主机名区分，集群部署时应显式配置。</p>
 *
 * <p>部分命名、参数校验与位运算源自 MyBatis-Plus 的 Sequence.java（Copyright (c) 2011-2025, baomidou (jobob@qq.com)，
 * Apache License 2.0，见 licenses/Apache-2.0.txt），其实现参考了 yu120/sequence（MIT）与 Twitter Snowflake（Apache License 2.0）；
 * 本类已在此基础上重写。</p>
 *
 * @author PONY
 */
public class SnowflakeSequence {
    private static final Logger logger = LoggerFactory.getLogger(SnowflakeSequence.class);
    /**
     * 时间起始标记点：2020-10-01 00:00:00 +08:00。作为基准，一旦确定不能变动
     */
    private static final long UJ_EPOCH = 1601481600000L;
    /**
     * 时间戳位数（毫秒）
     */
    private static final long TIMESTAMP_BITS = 42L;
    /**
     * 数据中心ID位数
     */
    private static final long DATACENTER_ID_BITS = 5L;
    /**
     * 工作机器ID位数
     */
    private static final long WORKER_ID_BITS = 5L;
    /**
     * 毫秒内序列号位数
     */
    private static final long SEQUENCE_BITS = 11L;

    public static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    public static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_TIMESTAMP = ~(-1L << TIMESTAMP_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    /**
     * 时间戳左移位数
     */
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
    /**
     * 时钟回拨达到该幅度时记录警告日志，同一轮回拨中幅度每再扩大该值记录一次（毫秒）
     */
    private static final long CLOCK_BACKWARDS_WARN_MILLIS = 1000L;

    /**
     * 数据中心ID
     */
    private final long datacenterId;
    /**
     * 工作机器ID
     */
    private final long workerId;
    /**
     * 毫秒内序列号
     */
    private long sequence = 0L;
    /**
     * 上次生成 ID 的时间戳（逻辑时钟，可能略超前于系统时间）
     */
    private long lastTimestamp = -1L;
    /**
     * 本轮回拨已记录警告的幅度，再扩大 {@link #CLOCK_BACKWARDS_WARN_MILLIS} 才记录下一条；系统时间追上后归零
     */
    private long warnedClockBackwards = 0L;

    /**
     * 根据网卡 MAC 地址、主机名、进程ID 自动推算数据中心ID和工作机器ID
     */
    public SnowflakeSequence() {
        this.datacenterId = deriveDatacenterId(MAX_DATACENTER_ID);
        this.workerId = deriveWorkerId(datacenterId, MAX_WORKER_ID);
        logger.info("Snowflake node id auto-derived from MAC/hostname/PID: datacenterId={}, workerId={}."
                + " Clustered deployments should set ujcms.datacenterId and ujcms.workerId explicitly.",
                datacenterId, workerId);
    }

    /**
     * 有参构造器
     *
     * @param datacenterId 数据中心ID
     * @param workerId     工作机器ID
     */
    public SnowflakeSequence(long datacenterId, long workerId) {
        Assert.isTrue(datacenterId >= 0 && datacenterId <= MAX_DATACENTER_ID,
                String.format("datacenter Id can't be greater than %d or less than 0", MAX_DATACENTER_ID));
        Assert.isTrue(workerId >= 0 && workerId <= MAX_WORKER_ID,
                String.format("worker Id can't be greater than %d or less than 0", MAX_WORKER_ID));
        this.datacenterId = datacenterId;
        this.workerId = workerId;
        logger.info("Snowflake node id configured: datacenterId={}, workerId={}", datacenterId, workerId);
    }

    public long getDatacenterId() {
        return datacenterId;
    }

    public long getWorkerId() {
        return workerId;
    }

    /**
     * 推算数据中心ID：网卡 MAC 地址 + 主机名。容器环境中 MAC 可能完全相同，主机名可区分
     */
    protected long deriveDatacenterId(long maxDatacenterId) {
        return hash(macAddresses() + "@" + hostName()) % (maxDatacenterId + 1);
    }

    /**
     * 推算工作机器ID：数据中心ID + 主机名 + 进程ID
     */
    protected long deriveWorkerId(long datacenterId, long maxWorkerId) {
        return hash(datacenterId + "@" + hostName() + "@" + ProcessHandle.current().pid()) % (maxWorkerId + 1);
    }

    /**
     * 所有非回环网卡的 MAC 地址，排序后拼接；无法获取时返回空串
     */
    private static String macAddresses() {
        TreeSet<String> macs = new TreeSet<>();
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (network.isLoopback()) {
                    continue;
                }
                byte[] mac = network.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    macs.add(HexFormat.of().formatHex(mac));
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to read MAC addresses: {}", e.getMessage());
        }
        return String.join(",", macs);
    }

    /**
     * 主机名：优先取环境变量（Linux/容器为 HOSTNAME，Windows 为 COMPUTERNAME），避免 DNS 反查；取不到时再查询本机地址
     */
    private static String hostName() {
        String name = System.getenv("HOSTNAME");
        if (name == null || name.isBlank()) {
            name = System.getenv("COMPUTERNAME");
        }
        if (name == null || name.isBlank()) {
            try {
                name = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                logger.warn("Failed to resolve host name: {}", e.getMessage());
                name = "";
            }
        }
        return name;
    }

    /**
     * SHA-256 摘要取前 8 字节作为非负整数，比 String.hashCode 分布均匀
     */
    private static long hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            long h = 0;
            for (int i = 0; i < Long.BYTES; i++) {
                h = (h << 8) | (digest[i] & 0xFF);
            }
            return h & Long.MAX_VALUE;
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必须提供的算法
            throw new IllegalStateException(e);
        }
    }

    /**
     * 获取下一个 ID
     *
     * @return 下一个 ID
     */
    public synchronized long nextId() {
        long now = now();
        boolean clockBackwards = now < lastTimestamp;
        // 时钟回拨：沿用上次时间戳作为逻辑时钟，既不等待也不抛异常
        long timestamp = clockBackwards ? lastTimestamp : now;
        long nextSequence;
        if (timestamp == lastTimestamp) {
            // 同一毫秒内序列号自增
            nextSequence = (sequence + 1) & SEQUENCE_MASK;
            if (nextSequence == 0) {
                // 序列号用尽：借用下一毫秒，无需自旋等待
                timestamp = lastTimestamp + 1;
            }
        } else {
            // 新的毫秒：序列号从 1 或 2 随机起始，避免低并发下 ID 全部为偶数
            nextSequence = ThreadLocalRandom.current().nextLong(1, 3);
        }

        long elapsed = timestamp - UJ_EPOCH;
        if (elapsed < 0) {
            throw new IllegalStateException(String.format(
                    "System clock %d is before the snowflake epoch %d", timestamp, UJ_EPOCH));
        }
        if (elapsed > MAX_TIMESTAMP) {
            throw new IllegalStateException(String.format(
                    "Snowflake timestamp exhausted: %d ms since epoch exceeds %d bits", elapsed, TIMESTAMP_BITS));
        }
        // 校验通过后才更新实例状态：异常的时钟读数（如远超 2160 年）不会污染逻辑时钟，时钟修正后即可恢复
        if (clockBackwards) {
            warnClockBackwards(lastTimestamp - now);
        } else if (warnedClockBackwards != 0) {
            warnedClockBackwards = 0;
            logger.info("System clock caught up with the logical clock; generated ids follow the system time again");
        }
        lastTimestamp = timestamp;
        sequence = nextSequence;
        // 时间戳部分 | 数据中心部分 | 机器标识部分 | 序列号部分
        return (elapsed << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | nextSequence;
    }

    /**
     * 回拨幅度达到阈值时记录警告；同一轮回拨中幅度再扩大一个阈值（序列号借用或再次回拨）才记录下一条，避免刷屏
     */
    private void warnClockBackwards(long offset) {
        if (offset - warnedClockBackwards >= CLOCK_BACKWARDS_WARN_MILLIS) {
            warnedClockBackwards = offset;
            logger.warn("Clock moved backwards by {} ms; continuing with the logical clock,"
                    + " generated ids run ahead of the system time until it catches up", offset);
        }
    }

    /**
     * 当前时间（毫秒）。可覆盖以便测试
     */
    protected long now() {
        return System.currentTimeMillis();
    }

    /**
     * 反解当前位分配生成的 ID 的时间戳部分，不适用于旧的 9 位序列号布局
     */
    public static long parseIdTimestamp(long id) {
        return (id >> TIMESTAMP_LEFT_SHIFT) + UJ_EPOCH;
    }
}
