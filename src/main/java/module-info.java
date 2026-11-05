/**
 * ZeoliteLib 模块声明：对外导出面 = item / menu / dialog 三个公共面包；
 * 骨架层与各实现包（com.zeoliteLib.internal、*.internal）一律不导出（零兼容承诺，06 §6），
 * 宿主包 com.zeoliteLib 是装配点，同样不导出。
 *
 * <p>边界说明（单模块形态的固有限制）：JPMS 不约束同模块内部互访——
 * menu 引用 item.internal 编译期放行，模块内纪律仍由 06 §4 依赖矩阵的口头约束承载；
 * 下方 exports 只对模块路径消费者生效，Paper 运行期与类路径消费者不受 JPMS 约束。
 */
module com.zeoliteLib {
    // paper-api 清单声明的 Automatic-Module-Name 是 org.bukkit（并非 io.papermc.paper）
    requires org.bukkit;
    // 26.0.2-1 起为显式模块化 jar（META-INF/versions/9/module-info.class）
    requires org.jetbrains.annotations;
    // Bukkit.getLogger() 的返回类型落在 java.logging（java.base 之外须显式声明）
    requires java.logging;
    // guava 33 签名上的注解依赖（paper-api 传递）；javax.inject / org.eclipse.sisu.inject
    // 虽也在传递图中，但 jar 无 Automatic-Module-Name、只落在类路径，requires 不到
    requires com.google.j2objc.annotations;
    requires org.checkerframework.checker.qual;

    // javac 拒绝导出空包——公共面现阶段只有 package-info，无任何类型；
    // 首个公共类落地时逐条启用：
    // exports com.zeoliteLib.item;    // InteractiveItem 公共面：可被 menu 与外部插件消费
    // exports com.zeoliteLib.menu;    // 箱子菜单公共面：可被外部插件消费
    // exports com.zeoliteLib.dialog;  // Dialog 公共面：可被外部插件消费
}
