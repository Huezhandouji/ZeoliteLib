package com.zeoliteLib.internal;

import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.ApiStatus;

/**
 * 模块懒初始化模板（06 §3.2）：once 守卫（每个 install 代次一次）+ 主线程断言 + 就绪检查 + 统一前缀日志。
 *
 * <p>模块管理器的标准形态（各模块同款）：
 * <pre>{@code
 * public final class XxxManager {
 *     private static final LazyModuleHost<XxxManager> HOST =
 *         LazyModuleHost.of("Xxx", XxxManager::create);
 *
 *     public static XxxManager get() { return HOST.get(); }
 *
 *     private static XxxManager create() {   // 仅骨架层回调
 *         JavaPlugin plugin = ModuleBootstrap.plugin();
 *         // 构造管理器 + registerEvents(listener, plugin) ...
 *         return new XxxManager(plugin);
 *     }
 * }
 * }</pre>
 *
 * <p>纪律（06 §3.4 第 2 条）：静态字段初始化只允许调用 {@link #of}（纯构造、零副作用），
 * 真正的初始化推迟到首次 {@link #get()}，保证 reload 干净；onEnable 不得触碰任何模块。
 *
 * <p>实现取舍：同 {@link ModuleBootstrap}——文档原文"包私有"因跨包访问无法成立，
 * 按"最小偏离、保住意图"处理为 internal 包内的 {@code public final} + 私有构造。
 *
 * <p>内部骨架件，零兼容承诺（06 §6）：外部插件不得 import 本类，
 * 升级后签名与语义变化不另行通知，升级碎掉是预期行为。
 */
@ApiStatus.Internal
public final class LazyModuleHost<T> {

    private final String moduleName;
    private final Supplier<T> initializer;

    /**
     * 缓存实例及其所属的 install 代次号（0 = 从未初始化；{@link ModuleBootstrap} 代次从 1 起）。
     * 代次与当前不一致（clear 后再 install，即非 reload 的 disable→enable 循环）即视为未初始化，
     * 重新执行 initializer——旧实例的监听器已随插件注销，不能复用。
     * 仅主线程访问（{@link #get()} 已做主线程断言，主线程串行），无需 volatile。
     */
    private int initializedGeneration;
    private T instance;

    /** 重入守卫：initializer 执行期间为 true，期间再次调用 {@link #get()} 抛 IllegalStateException。 */
    private boolean initializing;

    private LazyModuleHost(final String moduleName, final Supplier<T> initializer) {
        this.moduleName = moduleName;
        this.initializer = initializer;
    }

    /**
     * 创建一个懒初始化宿主。纯构造，不执行 initializer。
     *
     * @param moduleName 模块名，用于日志前缀 {@code [ZeoliteLib:<module>]} 与异常定位
     * @param initializer 首次 {@link #get()} 时执行的初始化逻辑
     * @throws IllegalArgumentException moduleName 为 null/空白，或 initializer 为 null
     */
    public static <T> LazyModuleHost<T> of(final String moduleName, final Supplier<T> initializer) {
        if (moduleName == null || moduleName.isBlank()) {
            throw new IllegalArgumentException("moduleName must not be null or blank (LazyModuleHost.of)");
        }
        if (initializer == null) {
            throw new IllegalArgumentException(
                "initializer must not be null (LazyModuleHost.of, module '" + moduleName + "')");
        }
        return new LazyModuleHost<>(moduleName, initializer);
    }

    /**
     * 取模块实例。每次调用都依次做：
     * <ol>
     *   <li>主线程断言：非主线程 → {@code IllegalStateException}；</li>
     *   <li>就绪检查：{@link ModuleBootstrap} 未就绪 → {@code IllegalStateException}
     *       （因此 {@code clear()} 之后本方法立即失效）；</li>
     *   <li>缓存命中且属于当前 install 代次则直接返回；否则执行 initializer 并缓存。
     *       clear 后再 install（非 reload 的 disable→enable）会使代次变化，
     *       旧实例（监听器已随插件注销）被丢弃并重新初始化。</li>
     * </ol>
     * initializer 抛异常（原样上抛）、返回 null（→ {@code IllegalStateException}）、
     * 或在执行期间重入本方法（→ {@code IllegalStateException}）均不缓存，下次调用重试；
     * 成功打一条 INFO 日志，前缀 {@code [ZeoliteLib:<module>]}（06 §3.4 第 3 条）——
     * 经服务器 Logger 输出以保证前缀唯一（插件 Logger 会再叠加 {@code [ZeoliteLib] }，造成双前缀）。
     *
     * @return 模块实例
     * @throws IllegalStateException 非主线程调用；ZeoliteLib 未启用；initializer 返回 null；
     *         初始化期间重入；initializer 自身抛出的异常原样上抛
     */
    public T get() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Module '" + moduleName
                + "' can only be accessed on the server main thread (current thread: "
                + Thread.currentThread().getName() + ")");
        }
        ModuleBootstrap.plugin(); // 就绪检查：未就绪抛 IllegalStateException("ZeoliteLib is not enabled")
        final int generation = ModuleBootstrap.generation();
        if (this.initializedGeneration == generation) {
            return this.instance;
        }
        if (this.initializing) {
            throw new IllegalStateException("Module '" + moduleName
                + "' initializer re-entered get() during its own initialization (circular initialization)");
        }
        this.initializing = true;
        try {
            final T created = this.initializer.get();
            if (created == null) {
                throw new IllegalStateException("Module '" + moduleName + "' initializer returned null");
            }
            this.instance = created;
            this.initializedGeneration = generation;
            Bukkit.getLogger().info("[ZeoliteLib:" + moduleName + "] module initialized");
            return created;
        } finally {
            this.initializing = false;
        }
    }
}
