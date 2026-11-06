/**
 * InteractiveItem 模块公共面包（模块槽位，06 §4）。
 *
 * <p><b>模块状态</b>：设计已定稿（02 架构设计 / 03 API 规格 / 04 事件与交互模型），
 * 实现为后续任务——骨架阶段本包只有本 package-info，不含任何类；
 * 公共类名、方法面以 03 规格的落地任务为准。
 *
 * <p><b>依赖方向（06 §4）</b>：只依赖 internal 骨架层（{@code ModuleBootstrap} / {@code LazyModuleHost}）；
 * 可被外部插件与 inventoryMenu 模块公共面消费；不依赖 inventoryMenu / dialog。
 *
 * <p><b>入口模式（06 §3.2 模板）</b>：唯一管理器类 {@code InteractiveItemManager}
 * 的静态 {@code get()} 经 {@code LazyModuleHost} 懒初始化——首调才构造、注册监听器并打日志；
 * 未用到的模块零开销（ADR-08）。
 */
package com.zeoliteLib.item;
