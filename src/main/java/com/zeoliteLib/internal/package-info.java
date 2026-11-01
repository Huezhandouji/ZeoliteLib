/**
 * ZeoliteLib 库级骨架层（06 §2）：全库共享的宿主接线件，不含任何模块逻辑。
 *
 * <p>本包只有两枚共享件（除此之外库级没有别的抽象，ADR-13/14）：
 * <ul>
 *   <li>{@link com.zeoliteLib.internal.ModuleBootstrap ModuleBootstrap}——插件实例持有与就绪状态；</li>
 *   <li>{@link com.zeoliteLib.internal.LazyModuleHost LazyModuleHost}——模块懒初始化模板。</li>
 * </ul>
 *
 * <p>依赖规则（06 §4）：骨架层谁也不 import，保持宿主中立；
 * 各模块（item / menu / dialog）只允许使用上述两枚共享件。
 *
 * <p><b>零兼容承诺（06 §6）</b>：本包对外部插件不承诺任何兼容性——
 * import internal 类视为违规使用，升级碎掉是预期行为。
 */
package com.zeoliteLib.internal;
