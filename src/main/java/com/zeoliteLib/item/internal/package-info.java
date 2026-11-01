/**
 * InteractiveItem 模块实现包（模块槽位，06 §4）。
 *
 * <p><b>模块状态</b>：设计已定稿（02 §2 内部分件职责边界、02 §3 包结构），
 * 实现为后续任务——骨架阶段本包只有本 package-info，不含任何类。
 *
 * <p><b>依赖方向</b>：只被 {@code com.zeoliteLib.item} 公共面使用；
 * menu / dialog 模块与外部插件一律不得 import 本包（跨模块消费只走对方公共面，06 §3.4 第 1 条）。
 *
 * <p><b>零兼容承诺（06 §6）</b>：本包类对外部插件不承诺任何兼容性（Hyrum 定理防线，02 §3）。
 */
package com.zeoliteLib.item.internal;
