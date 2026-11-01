/**
 * Dialog 对话框封装模块公共面包（槽位预留，06 §4）。
 *
 * <p><b>模块状态</b>：待设计（设计文档为后续任务）——骨架阶段本包只有本 package-info，
 * 不含任何类。基线已确认：paper-api 1.21.11 含 {@code io.papermc.paper.dialog.Dialog} /
 * {@code DialogResponseView} 及 {@code registry.data.dialog} 构建面；
 * 封装目标 = 降低 registry 式 API 的使用成本（06 §4 槽位表）。
 *
 * <p><b>依赖方向（06 §4）</b>：零模块依赖，只依赖 internal 骨架层；可被外部插件消费。
 *
 * <p><b>入口模式（06 §3.2 模板）</b>：唯一管理器类的静态 {@code get()} 经 {@code LazyModuleHost}
 * 懒初始化；公共类名、方法面在本模块设计文档定稿前一律不承诺（06 §4）。
 */
package com.zeoliteLib.dialog;
