# ForceResourcePack 插件
This is a plugin for forcing the loading of Minecraft resource packs.
版本：1.21.4
作者：yixiao
Minecraft 兼容版本：1.19 – 1.21（Paper/Spigot）
Java 版本要求：Java 17+

项目简介

ForceResourcePack 是一个用于 Minecraft 服务器的 资源包强制插件，可确保玩家在加入服务器时自动下载并使用指定的资源包。

功能特点：

强制资源包：玩家未安装资源包将被踢出服务器（可在配置中自定义）。

SHA-1 验证：支持资源包哈希验证，确保玩家下载的是官方资源包。

可自定义提示信息：所有插件消息、玩家提示、谢谢支持信息均可在配置文件中自定义。

聊天前缀：所有插件发向玩家或世界聊天的消息自动加上 [ResourcePack] 红色前缀

自动下载与更新：玩家加入或切换世界时，自动检查资源包版本和完整性

稳定异常处理：全局事件监听和异步操作保证服务器稳定运行
