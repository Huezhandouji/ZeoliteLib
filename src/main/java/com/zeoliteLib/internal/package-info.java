/**
 * ZeoliteLib 库级骨架层（06 §2）：全库共享的宿主接线件，不含任何模块逻辑。
 *
 * <p>本包现有三枚共享件（前两枚为 ADR-13/14 所列骨架件，
 * {@link com.zeoliteLib.internal.ZeoliteEventBus ZeoliteEventBus} 为后续新增的库内通信原语）：
 * <ul>
 *   <li>{@link com.zeoliteLib.internal.ModuleBootstrap ModuleBootstrap}——插件实例持有与就绪状态；</li>
 *   <li>{@link com.zeoliteLib.internal.LazyModuleHost LazyModuleHost}——模块懒初始化模板；</li>
 *   <li>{@link com.zeoliteLib.internal.ZeoliteEventBus ZeoliteEventBus}——非静态事件总线
 *       （事件基类 {@link com.zeoliteLib.internal.ZeoliteEvent ZeoliteEvent}，编译期强制、
 *       含建议性取消标志；模块经 {@code get()} 取共享实例，模块间解耦通信）。</li>
 * </ul>
 *
 * <p>依赖规则（06 §4）：骨架层谁也不 import，保持宿主中立；
 * 各模块（item / inventoryMenu / dialog）只允许使用上述共享件。
 *
 * <p><b>零兼容承诺（06 §6）</b>：本包对外部插件不承诺任何兼容性——
 * import internal 类视为违规使用，升级碎掉是预期行为。
 */
package com.zeoliteLib.internal;
