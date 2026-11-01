/**
 * 箱子菜单模块公共面包（槽位预留，06 §4）。
 *
 * <p><b>模块状态</b>：待设计（设计文档为后续任务）——骨架阶段本包只有本 package-info，
 * 不含任何类。已知边界：消费 item 公共面（按钮 = definition + place，路由 = context；
 * 02 §7 已留两个钩子：context 容器事实 + place 返回副本）。
 *
 * <p><b>依赖方向（06 §4）</b>：只依赖 item 公共面与 internal 骨架层；可被外部插件消费。
 *
 * <p><b>入口模式（06 §3.2 模板）</b>：唯一管理器类的静态 {@code get()} 经 {@code LazyModuleHost}
 * 懒初始化；公共类名、方法面在本模块设计文档定稿前一律不承诺（06 §4）。
 */
package com.zeoliteLib.menu;
