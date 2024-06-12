package com.ujcms.commons.db.identifier;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 雪花算法ID
 *
 * <p>自增序列从12位减少为9位，每毫秒可产生ID从4096个变为512个，每秒可产生ID数为512000个，足够几乎所有场合。</p>
 * <p>时间戳部分从41位增加到44位，可使用年限从约70年增加到约560年，足够使用到计算机系统完全重构。</p>
 *
 * <p>优化开源项目：https://github.com/baomidou/mybatis-plus/blob/master/mybatis-plus-core/src/main/java/com/baomidou/mybatisplus/core/toolkit/Sequence.java</p>
 *
 * @author PONY
 */
public class SnowflakeSequence {
    private static final Log logger = LogFactory.getLog(SnowflakeSequence.class);
    /**
     * 时间起始标记点，作为基准，一般取系统的最近时间（一旦确定不能变动）
     */
    private static final long UJ_EPOCH = 1601481600000L;
    /**
     * 机器标识位数
     */
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    public static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    public static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    /**
     * 毫秒内自增位
     */
    private static final long SEQUENCE_BITS = 9L;
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    /**
     * 时间戳左移动位
     */
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
    /**
     * 数据中心ID
     */
    private final long datacenterId;
    /**
     * 工作机器ID
     */
    private final long workerId;
    /**
     * 并发控制
     */
    private long sequence = 0L;
    /**
     * 上次生产 ID 时间戳
     */
    private long lastTimestamp = -1L;

    public SnowflakeSequence(@Nullable InetAddress inetAddress) {
        this.datacenterId = getDatacenterId(inetAddress, MAX_DATACENTER_ID);
        this.workerId = getWorkerId(datacenterId, MAX_WORKER_ID);
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
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /**
     * 获取 数据中心ID
     */
    protected long getDatacenterId(@Nullable InetAddress inetAddress, long maxDatacenterId) {
        try {
            if (null == inetAddress) {
                inetAddress = InetAddress.getLocalHost();
            }
            NetworkInterface network = NetworkInterface.getByInetAddress(inetAddress);
            if (null == network) {
                return 1L;
            } else {
                byte[] mac = network.getHardwareAddress();
                if (null != mac) {
                    long id = ((0x000000FF & (long) mac[mac.length - 2]) | (0x0000FF00 & (((long) mac[mac.length - 1]) << 8))) >> 6;
                    id = id % (maxDatacenterId + 1);
                    return id;
                }
            }
        } catch (Exception e) {
            logger.warn(" getDatacenterId: " + e.getMessage());
        }
        return 0L;
    }

    /**
     * 获取 工作机器ID
     */
    protected long getWorkerId(long datacenterId, long maxWorkerId) {
        StringBuilder pid = new StringBuilder();
        pid.append(datacenterId);
        String name = ManagementFactory.getRuntimeMXBean().getName();
        if (StringUtils.isNotBlank(name)) {
            String at = "@";
            // GET jvmPid
            pid.append(name.split(at)[0]);
        }
        // MAC + PID 的 hashcode 获取16个低位
        return (pid.toString().hashCode() & 0xffff) % (maxWorkerId + 1);
    }

    /**
     * 获取下一个 ID
     *
     * @return 下一个 ID
     */
    public synchronized long nextId() {
        long timestamp = now();
        // 闰秒
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            int maxOffset = 5;
            if (offset <= maxOffset) {
                try {
                    while (timestamp < lastTimestamp) {
                        wait(offset * 2);
                        timestamp = now();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                throw new IllegalStateException(String.format("Clock moved backwards. Refusing to generate id for %d milliseconds", offset));
            }
        }
        if (lastTimestamp == timestamp) {
            // 相同毫秒内，序列号自增
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 同一毫秒的序列数已经达到最大
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒内，序列号置为 1 - 3 随机数
            sequence = ThreadLocalRandom.current().nextLong(1, 3);
        }

        lastTimestamp = timestamp;

        // 时间戳部分 | 数据中心部分 | 机器标识部分 | 序列号部分
        return ((timestamp - UJ_EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = now();
        while (timestamp <= lastTimestamp) {
            timestamp = now();
        }
        return timestamp;
    }

    protected long now() {
        return SystemClock.now();
    }

    /**
     * 反解id的时间戳部分
     */
    public static long parseIdTimestamp(long id) {
        return (id >> (SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS)) + UJ_EPOCH;
    }
}
