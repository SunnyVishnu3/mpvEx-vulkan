# LSPosed official wiki

基于 Riru/Zygisk 的 ART hook 框架，提供与原版 Xposed 相同的 API, 使用 [LSPlant](https://github.com/LSPosed/LSPlant) 进行 hook。

> Xposed 框架是一套开放源代码的框架服务，可以在不修改APK文件的情况下修改目标应用的运行，基于它可以制作功能强大的模块，且在功能不冲突的情况下同时运作。

LSPosed 框架和原版 Xposed 框架的不同之处

1. LSPosed 支持 Android 8.1 以后的版本
2. LSPosed 只有注入被勾选的应用，其他应用运行在干净的环境
3. 对于不需要注入系统服务的模块，重启目标应用即可激活
4. LSPosed 极难被检测，文件系统没有可疑痕迹，不需要安装独立的管理器应用

## Chat group / 用户讨论群组

- Telegram channel: [@LSPosed](https://t.me/LSPosed)

## Catalog / 目录

### For User / 用户
- [How to use it](https://github.com/LSPosed/LSPosed/wiki/How-to-use-it) / [如何使用](https://github.com/LSPosed/LSPosed/wiki/%E5%A6%82%E4%BD%95%E4%BD%BF%E7%94%A8)
### For Developer / 开发者
- [New XSharedPreferences](https://github.com/LSPosed/LSPosed/wiki/New-XSharedPreferences)
- [Module Scope](https://github.com/LSPosed/LSPosed/wiki/Module-Scope)
- [Native Hook](https://github.com/LSPosed/LSPosed/wiki/Native-Hook)
