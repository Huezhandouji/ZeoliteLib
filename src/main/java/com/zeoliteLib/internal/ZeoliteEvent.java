package com.zeoliteLib.internal;

import org.jetbrains.annotations.ApiStatus;

/**
 * 库内事件基类：所有上 {@link ZeoliteEventBus} 的事件必须继承本类——
 * {@code subscribe}/{@code publish} 的类型参数以本类为上界，编译期强制。
 *
 * <p>取消语义（建议性标志，消费方式对齐 Bukkit {@code Cancellable}）：
 * {@link #setCanceled(boolean)} 只置位，<b>不中断总线分发</b>——后序处理器照常收到事件，
 * 是否中止原操作由发布方在 {@code publish} 返回后检查 {@link #isCanceled()} 决定
 * （典型用法：交互前发布"将发生 X"事件，任一模块取消即跳过默认行为）。
 *
 * <p>使用约定：事件对象为一次性载体，每次发布应新建实例——canceled 不随 publish 重置，
 * 复用实例会把上一次的取消状态带进本次分发。子类载荷建议用 final 字段承载。
 *
 * <p>单线程使用（随总线纪律），canceled 无需 volatile。
 *
 * <p>落位说明：暂随总线居 internal（库现阶段零导出面）；日后某模块公共 API
 * 需要对外暴露事件时，事件基类须随迁公共面包重新评估，见 module-info 导出注。
 *
 * <p>内部骨架件，零兼容承诺（06 §6）：外部插件不得 import 本类，
 * 升级后签名与语义变化不另行通知，升级碎掉是预期行为。
 */
public abstract class ZeoliteEvent {

    /** 取消标志；false = 未取消（初始态）。 */
    private boolean canceled = false;

    /** 仅供子类构造。 */
    protected ZeoliteEvent() {
    }

    /**
     * 是否已被任一处理器取消。
     *
     * @return true = 已取消，发布方应跳过原操作
     */
    public boolean isCanceled() {
        return canceled;
    }

    /**
     * 置位/撤销取消。只改标志，不影响本次分发（见类注取消语义）。
     *
     * @param canceled true = 取消，false = 撤销取消
     */
    public void setCanceled(final boolean canceled) {
        this.canceled = canceled;
    }
}
