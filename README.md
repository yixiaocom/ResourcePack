# ForceResourcePack  
项目简介

ForceResourcePack 是一个用于 Minecraft 服务器的 资源包强制插件，可确保玩家在加入服务器时自动下载并使用指定的资源包。

功能特点：

强制资源包：玩家未安装资源包将被踢出服务器（可在配置中自定义）。

SHA-1 验证：支持资源包哈希验证，确保玩家下载的是官方资源包。

可自定义提示信息：所有插件消息、玩家提示、谢谢支持信息均可在配置文件中自定义。

自动下载与更新：玩家加入或切换世界时，自动检查资源包版本和完整性。

下载资源可以选择阿里云oss或自己自定义链接

###食用小技巧：
~~下载并拖入plugins~~
### 配置

插件会在启动时自动生成一个默认的 `config.yml` 配置文件。配置文件的内容如下：

```yaml
# 配置资源包下载链接的类型
# 可选值: 'oss'（使用 OSS 链接）或 'customUrl'（使用自定义链接）
downloadType: "oss"

# OSS 配置
oss:
  endpoint: "https://oss-cn-chengdu.aliyuncs.com"
  accessKeyId: "your-access-key-id"
  accessKeySecret: "your-access-key-secret"
  bucketName: "your-bucket-name"
  objectName: "your-object-name"

# 自定义 URL 配置，如果选择 'customUrl' 类型时生效
customUrl: "https://your-custom-url.com/resourcepack.zip"

# 资源包 URL 的有效期（秒）
expirationSeconds: 3600

# 玩家下载失败时的消息
downloadFailedMessage: "资源包下载失败，请联系管理员！"

# 玩家加入时的欢迎消息
joinMessage: "谢谢支持资源包！"
