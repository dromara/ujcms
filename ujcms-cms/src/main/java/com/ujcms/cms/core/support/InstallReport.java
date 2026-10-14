package com.ujcms.cms.core.support;

import java.util.ArrayList;
import java.util.List;

/**
 * 安装报告（install-flow §7）：无人值守安装引擎的逐步执行记录（R2 风格的可诉原因行）。
 * 任一步骤失败即不翻转完成——状态停留 CORE_READY，报告整体落启动日志，下次启动重试
 * （每步幂等）。
 *
 * @author PONY
 */
public class InstallReport {
    /**
     * 步骤状态：成功 / 跳过（含幂等续装的"已就绪"）/ 失败
     */
    public enum Status {
        OK, SKIPPED, FAILED
    }

    /**
     * 单步记录
     *
     * @param step   步骤名（如 {@code site1} / {@code theme[site1]} / {@code site[English Site]}）
     * @param status 执行状态
     * @param reason 可诉原因行（成功时为结果摘要）
     */
    public record Step(String step, Status status, String reason) {
    }

    private final List<Step> steps = new ArrayList<>();

    public void ok(String step, String reason) {
        steps.add(new Step(step, Status.OK, reason));
    }

    public void skipped(String step, String reason) {
        steps.add(new Step(step, Status.SKIPPED, reason));
    }

    public void failed(String step, String reason) {
        steps.add(new Step(step, Status.FAILED, reason));
    }

    public List<Step> getSteps() {
        return steps;
    }

    public boolean hasFailure() {
        return steps.stream().anyMatch(s -> s.status() == Status.FAILED);
    }

    /**
     * 多行格式化（落日志）：每步一行 {@code - [STATUS] step: reason}。
     */
    public String format() {
        StringBuilder sb = new StringBuilder("InstallReport:");
        for (Step step : steps) {
            sb.append("\n  - [").append(step.status()).append("] ").append(step.step())
                    .append(": ").append(step.reason());
        }
        return sb.toString();
    }
}
