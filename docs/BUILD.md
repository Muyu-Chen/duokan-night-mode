# 构建说明

bootstrap-sdk.py 从 Google 官方 SDK 下载两个固定版本归档并检查已记录的 SHA-1；只解压 android.jar、aapt2、dx.jar 和 apksigner.jar 到 .local/android-sdk。所有下载和构建输出都被 .gitignore 排除。

使用已有 SDK 时可传入 --sdk <SDK目录>，并通过 --build-tools 指定提供 dx.jar 的构建工具版本。Windows 为已验证构建环境。

签名参数只接受本地路径，不在源文件或 CI 配置中嵌入签名材料。匹配的 AOSP 公开测试材料可从官方源码 [target/product/security](https://android.googlesource.com/platform/build/+/refs/heads/main/target/product/security/) 自行取得；本项目不重新分发签名材料。

build.py 只使用本项目源码、公开 Android API 与自己的图标资源；不需要读取或拉取原厂 APK。默认签名目标为兼容表中已验证的证书。不要将未知签名的辅助包当作兼容结果。

GitHub Actions 只构建未签名 APK，并检查公开文件范围；正式安装包由维护者在本地签名、检查后发布。
