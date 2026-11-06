/**
 * ZeoliteLib 模块声明：对外导出面 = item / inventoryMenu / dialog 三个公共面包；
 * 骨架层与各实现包（com.zeoliteLib.internal、*.internal）一律不导出（零兼容承诺，06 §6），
 * 宿主包 com.zeoliteLib 是装配点，同样不导出。
 *
 * <p>边界说明（单模块形态的固有限制）：JPMS 不约束同模块内部互访——
 * inventoryMenu 引用 item.internal 编译期放行，模块内纪律仍由 06 §4 依赖矩阵的口头约束承载；
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
    // InventoryMenu 构建时经 adventure 把 legacy § 标题转为 Component（Paper 已过时 String 标题重载）
    requires net.kyori.adventure;
    requires net.kyori.adventure.text.serializer.legacy;

    // javac 拒绝导出空包；首个公共类落地时逐条启用：
    // inventoryMenu 公共面的回调签名引用 item 公共面的事件类型，item / item.event 须一并导出
    exports com.zeoliteLib.item;           // ItemInteraction 公共面：可被 inventoryMenu 与外部插件消费
    exports com.zeoliteLib.item.event;     // 交互事件载体：InventoryMenu 回调签名的参数类型
    exports com.zeoliteLib.inventoryMenu;  // 箱子菜单公共面：InventoryMenu 基类，可被外部插件消费
    // exports com.zeoliteLib.dialog;  // Dialog 公共面：可被外部插件消费（暂无公共类，保持注释）
}
