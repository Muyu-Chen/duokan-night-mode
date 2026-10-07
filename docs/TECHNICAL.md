# 兼容性与实现

## 运行时主题替换

NightSession 继承 Android Instrumentation，通过 targetPackage 和 targetProcesses 指定原生阅读进程。在 EInkReadingActivity 创建/恢复后，有界等待实际阅读控制器就绪。

仅匹配 EInkTxtController 与 EInkEpubController，反射获取其 mEinkTheme，再使用 Java Proxy 实现 IEInkReadingThemeInterface。三个方法分别返回黑色背景、白色正文与浅灰状态文字。随后调用原生 ReadingFeature.applyPrefs；图片、排版、翻页及 E-Ink 刷新仍由原生阅读器完成。

这是一段独立原创适配代码。类名和接口名是运行时查找所必需的兼容信息，仓库不分发目标应用的实现代码。

## 签名条件

已验证目标证书与 AOSP platform 公共测试证书一致，其他签名不能直接假定兼容。签名材料只在本地用于生成独立辅助包，不放进 Git。

- [Android Instrumentation 官方说明](https://source.android.com/docs/core/tests/development/instr-app-e2e)
- [AOSP 测试签名与发布签名说明](https://source.android.com/docs/core/ota/sign_builds)
- [Java Proxy 文档](https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/Proxy.html)

辅助应用无请求权限、无 sharedUserId，安装在普通应用目录。Instrumentation 运行时使用目标进程的上下文，因此其生命周期和进程优先级可能与普通阅读状态不同，后台行为仍待测量。

## 当前状态

辅助页面每次恢复时产生随机查询 nonce，向仍在运行的夜间会话查询。会话通过 StateProvider 回复；provider 校验调用方为已安装阅读器 UID，且与辅助应用签名一致。只保存辅助应用自己的模式/nonce，不读写阅读数据。超时没有回复时，不沿用历史开启状态。

点击切换后显示三秒倒计时；NightSession 开启临时配色，DaySession 替换该会话后退出。随后通过 PackageManager 的原生启动 Intent 打开多看。

## 恢复

点击“恢复白底黑字”即可结束夜间会话；正常重启设备也会清除运行时对象替换。本公开版本不修改原生夜间偏好，不包含书库清理、卸载、刷机或系统分区写入逻辑。
