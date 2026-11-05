package com.zeoliteLib.internal;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 库内事件总线（非静态实例形态）：按事件类型订阅/发布，事件基类 {@link ZeoliteEvent}
 * （编译期强制），{@link Subscription} 令牌可取消订阅。
 *
 * <p>与静态总线（如各插件自建的全局 GameEventBus）不同，本类每个实例持有独立订阅表。
 * 两条获取路径，分工不同：
 * <ul>
 *   <li><b>库内共享实例</b>：{@link #get()}——{@link LazyModuleHost} 宿主，同各模块管理器的
 *       标准形态（06 §3.2）。跨模块通信一律用它；每个 install 代次一个新实例，
 *       非 reload 的 disable→enable 循环中旧实例连同其全部订阅一并作废
 *       （对齐模块监听器随插件 HandlerList 注销的既有纪律），模块须在自己的初始化里重新订阅；</li>
 *   <li><b>自建实例</b>：构造函数——隔离事件流或脱离服务器的测试用，
 *       生命周期完全归创建者，不经就绪检查与主线程断言。</li>
 * </ul>
 *
 * <p>分发语义（接口合约，调用方可以依赖）：
 * <ul>
 *   <li><b>层级分发</b>：按 {@code event.getClass()} 起沿继承链上行至 {@link ZeoliteEvent}
 *       （含根）逐层查表——子类事件会同时送达其各父类直至根的订阅者，父类订阅者不收子类之外的事件；
 *       层间先子后父（最特殊优先，先到的事件类型先处理），同层内按订阅顺序，
 *       同一处理器在链上多层订阅则按层各收一次；</li>
 *   <li><b>按订阅顺序同步分发</b>：同一事件类型内先订阅先收到；</li>
 *   <li><b>快照迭代</b>：发布入口对整条分发链的订阅列表一次性取快照，处理器在回调中
 *       订阅/退订不影响本次分发——含对尚未轮到的父类层的新订阅同样不送达本次事件，
 *       本次发布前已订阅者即使刚被退订也仍会收到；</li>
 *   <li><b>异常隔离</b>：单个处理器抛出任意 {@link Throwable} 不中断其余处理器
 *       （一个坏监听器不得饿死同事件的其他订阅者），以 SEVERE 记日志后继续分发；</li>
 *   <li><b>取消不中断分发</b>：处理器置 canceled（{@link ZeoliteEvent#setCanceled}）
 *       只置位，后序处理器照常收到；是否中止原操作由发布方在 publish 返回后
 *       检查 {@link ZeoliteEvent#isCanceled()} 决定（publish 回传处理过的同一实例，见方法注）。</li>
 * </ul>
 *
 * <p>线程合约：非线程安全（HashMap/ArrayList）——订阅、退订、发布须在同一线程内完成
 * （约定服务器主线程，与 {@link LazyModuleHost} 的主线程纪律一致）。
 * 实例方法自身不做主线程断言：本类为纯 Java 数据结构，不触碰游戏状态，
 * 异步线程单线程使用同样合法；共享实例经 {@link #get()} 取用时自带断言（{@link LazyModuleHost#get()}）。
 *
 * <p>内部骨架件，零兼容承诺（06 §6）：外部插件不得 import 本类，
 * 升级后签名与语义变化不另行通知，升级碎掉是预期行为。
 */
@ApiStatus.Internal
public final class ZeoliteEventBus {

    /** 处理器异常日志走 JUL（类名 Logger），保持本类零 Bukkit 依赖、可在无服务器环境使用。 */
    private static final Logger LOGGER = Logger.getLogger(ZeoliteEventBus.class.getName());

    /**
     * 共享实例宿主：同各模块管理器的标准形态。静态字段初始化只调用 {@link LazyModuleHost#of}
     * （initializer 为纯构造 {@code ZeoliteEventBus::new}，零副作用，06 §3.4 第 2 条）。
     */
    private static final LazyModuleHost<ZeoliteEventBus> HOST =
        LazyModuleHost.of("event_bus", ZeoliteEventBus::new);

    /** eventType → 有序订阅列表（订阅序即分发序）；空列表随最后一次退订移除，防键累积。 */
    private final Map<Class<?>, List<Consumer<?>>> listeners = new HashMap<>();

    /**
     * 取库内共享事件总线——模块间通信的唯一入口：主线程断言 + 就绪检查 + 每 install 代次一个新实例。
     * 代次更替（非 reload 的 disable→enable）后旧实例作废、订阅不跨代存活，
     * 模块须在自己的初始化（如 {@code create()}）里重新订阅。
     *
     * @return 当前代次的共享总线实例
     * @throws IllegalStateException 非主线程调用；ZeoliteLib 未启用（未 install 或已 clear）
     */
    public static ZeoliteEventBus get() {
        return HOST.get();
    }

    /**
     * 创建一个空总线（隔离事件流或测试用；跨模块通信用 {@link #get()}）。
     * 实例即隔离域：与任何其他实例互不相通。
     */
    public ZeoliteEventBus() {
    }

    /**
     * 订阅事件。同一处理器可对同一事件类型重复订阅（按次收到、各自持独立令牌）。
     *
     * <p>handler 逆变（{@code Consumer<? super T>}，PECS）：接受键类型或其父类事件的
     * 处理器均可订阅——如一个 {@code Consumer<ZeoliteEvent>} 审计器可订阅任意具体事件类型，
     * 与层级分发配套（订阅父类即收子类，处理器按父类视角消费）。
     *
     * @param eventType 事件类型（须为 {@link ZeoliteEvent} 子类，编译期强制；层级匹配——
     *                   本类型及其所有子类的事件均会送达，见类注）
     * @param handler   事件处理器
     * @return 可取消的订阅令牌
     * @throws IllegalArgumentException eventType 或 handler 为 null
     */
    public <T extends ZeoliteEvent> Subscription subscribe(final Class<T> eventType, final Consumer<? super T> handler) {
        if (eventType == null) {
            throw new IllegalArgumentException("eventType must not be null (ZeoliteEventBus.subscribe)");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null (ZeoliteEventBus.subscribe)");
        }
        final List<Consumer<?>> list = listeners.computeIfAbsent(eventType, k -> new ArrayList<>());
        list.add(handler);
        return new Subscription(eventType, handler);
    }

    /**
     * 发布事件：按 {@code event.getClass()} 起沿继承链层级匹配（先子后父，见类注），
     * 快照迭代同步分发，异常隔离见类注。无订阅者时为空操作。
     * 取消为建议性标志——分发不因 canceled 中断，
     * 发布方应在返回后检查 {@link ZeoliteEvent#isCanceled()} 决定是否继续原操作。
     *
     * <p>回传同一实例：事件为一次性可变载体、按引用分发，返回值即入参本身
     * （非拷贝），已含处理器写入的取消标志与载荷变更——支持链式消费形态
     * {@code if (bus.publish(event).isCanceled()) ...}，与先发布后持引用检查完全等价。
     *
     * @param event 事件实例（每次发布新建，勿复用，见 {@link ZeoliteEvent} 类注）
     * @return 同一事件实例；类型参数保留编译期具体子类型，调用方免强转即可读子类载荷
     * @throws IllegalArgumentException event 为 null
     */
    public <T extends ZeoliteEvent> T publish(final T event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null (ZeoliteEventBus.publish)");
        }
        //发布入口对全链一次性取快照（先子后父拼平）：回调中对任一层——含尚未轮到的父类层——
        //订阅/退订均不影响本次分发，与类注快照合约一致
        final List<Consumer<?>> dispatchList = new ArrayList<>();
        for (Class<?> type = event.getClass(); ZeoliteEvent.class.isAssignableFrom(type); type = type.getSuperclass()) {
            final List<Consumer<?>> handlers = listeners.get(type);
            if (handlers != null) {
                dispatchList.addAll(handlers);
            }
        }
        for (final Consumer<?> handler : dispatchList) {
            dispatch(event, handler);
        }
        return event;
    }

    /**
     * 单个处理器分发。强转安全性由 subscribe 的键控保证：订阅时以 {@code Class<T>}
     * （T 上界为 {@link ZeoliteEvent}）为键、存入 {@code Consumer<? super T>}——
     * 处理器接受的事件类型为键类型或其父类，而层级分发只按事件继承链上的类型查表，
     * 事件运行时类型必为键类型的子类，故查出的处理器必然接受该事件实例。
     */
    @SuppressWarnings("unchecked")
    private void dispatch(final ZeoliteEvent event, final Consumer<?> handler) {
        try {
            ((Consumer<ZeoliteEvent>) handler).accept(event);
        } catch (final Throwable t) {
            //一个坏处理器不得饿死其余订阅者；Error 一并拦截以保证分发完整，交由日志暴露
            LOGGER.log(Level.SEVERE, "[ZeoliteLib:event-bus] handler for event "
                + event.getClass().getName() + " threw, continuing dispatch", t);
        }
    }

    /** 退订（幂等）。仅 {@link Subscription#unsubscribe()} 调用。 */
    private void unsubscribe(final Class<?> eventType, final Consumer<?> handler) {
        final List<Consumer<?>> list = listeners.get(eventType);
        if (list == null) {
            return;
        }
        list.remove(handler);
        if (list.isEmpty()) {
            listeners.remove(eventType);
        }
    }

    /**
     * 清空本实例全部订阅（幂等）。实例本身随 GC 回收即释放，无需显式清理——
     * 仅复用实例时才需要（对比静态总线 disable 时必须 unsubscribeAll 的理由）。
     */
    public void unsubscribeAll() {
        listeners.clear();
    }

    /**
     * 订阅令牌：绑定创建它的总线实例（非静态内部类，跨实例令牌无法成立），
     * {@link #unsubscribe()} 幂等，重复调用无害。
     */
    public final class Subscription {

        private final Class<?> eventType;
        private final Consumer<?> handler;
        private boolean unsubscribed = false;

        private Subscription(final Class<?> eventType, final Consumer<?> handler) {
            this.eventType = eventType;
            this.handler = handler;
        }

        /** 取消订阅；已取消再调用为空操作。 */
        public void unsubscribe() {
            if (!unsubscribed) {
                ZeoliteEventBus.this.unsubscribe(eventType, handler);
                unsubscribed = true;
            }
        }

        /** 是否已取消。 */
        public boolean isUnsubscribed() {
            return unsubscribed;
        }
    }
}
