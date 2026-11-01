package com.zeoliteLib.internal;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;

/**
 * 库级骨架件：持有 ZeoliteLib 插件实例与就绪状态（06 §3.1 正式规格，ADR-08）。
 *
 * <p>只管实例与状态，不管任何模块——模块的懒初始化由 {@link LazyModuleHost} 承担。
 * 生命周期接线：{@code ZeoliteLibPlugin.onEnable} 调用 {@link #install(JavaPlugin)}，
 * {@code onDisable} 调用 {@link #clear()}。清除后各模块 {@code get()} 立即失效
 * （{@link LazyModuleHost#get()} 每次调用都做就绪检查）。
 *
 * <p>实现取舍：设计文档原文写作"包私有"，但宿主类（{@code com.zeoliteLib}）与各模块管理器
 * （{@code com.zeoliteLib.<module>}）都需要跨包访问，Java 包私有无法成立；故按"最小偏离、保住意图"
 * 处理——类放本 internal 包、{@code public final}、私有构造，配合下方兼容承诺声明守住"内部件"意图。
 *
 * <p>内部骨架件，零兼容承诺（06 §6）：外部插件不得 import 本类，
 * 升级后签名与语义变化不另行通知，升级碎掉是预期行为。
 */
@ApiStatus.Internal
public final class ModuleBootstrap {

    /** 已登记的插件实例；null = 未就绪。volatile 仅为跨线程读（如调试钩子）提供可见性，不构成线程安全承诺。 */
    private static volatile JavaPlugin plugin;

    /**
     * install 代次号：每次成功 install 递增。供 {@link LazyModuleHost} 识别"clear 后再 install"
     * （非 reload 的 disable→enable 循环，类加载器不重建）——该场景下旧模块实例的监听器已随
     * 插件注销，必须重新初始化而不能复用缓存。
     */
    private static volatile int generation;

    private ModuleBootstrap() {
    }

    /**
     * 登记 ZeoliteLib 插件实例。仅 {@code ZeoliteLibPlugin.onEnable} 调用，除此之外不做任何事
     * （不注册监听器、不触碰任何模块，06 §5）。每次成功登记使 install 代次号递增，
     * 已缓存的模块实例在下次 {@code get()} 时重新初始化。
     *
     * @param plugin 插件实例
     * @throws IllegalArgumentException plugin 为 null
     * @throws IllegalStateException 重复 install（未 clear 的二次 enable）
     */
    public static void install(final JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin must not be null (ModuleBootstrap.install)");
        }
        if (ModuleBootstrap.plugin != null) {
            throw new IllegalStateException(
                "ZeoliteLib module bootstrap is already installed (double install without clear)");
        }
        ModuleBootstrap.plugin = plugin;
        generation++;
    }

    /**
     * 清除登记的插件实例。仅 {@code ZeoliteLibPlugin.onDisable} 调用；幂等（重复调用无害）。
     * 清除后各模块 {@code get()} 立即失效；模块监听器随插件自动注销（HandlerList），骨架不做额外清理。
     */
    public static void clear() {
        plugin = null;
    }

    /**
     * 当前插件实例，供模块懒初始化时注册监听器等取用。
     *
     * @return 已登记的插件实例
     * @throws IllegalStateException 未就绪（未 install 或已 clear）
     */
    public static JavaPlugin plugin() {
        final JavaPlugin current = plugin;
        if (current == null) {
            throw new IllegalStateException("ZeoliteLib is not enabled");
        }
        return current;
    }

    /** 是否处于就绪状态（install 之后、clear 之前）。 */
    public static boolean isReady() {
        return plugin != null;
    }

    /**
     * 当前 install 代次号（包私有，仅供 {@link LazyModuleHost} 比对缓存是否属于本次 enable）。
     * 不属于 06 §3.1 公共面，外部不可见。
     */
    static int generation() {
        return generation;
    }
}
