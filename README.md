# IM-SYNC-BOT

im同步机器人，现主要同步（转发）tg跟qq群。

## Upgrade

若是从旧版本迁移上来，请注意本版本不向下兼容，需要重新按照下面修改配置文件。

## Quickstart

把 [项目源码](https://github.com/KurenaiRyu/im-sync-bot/releases) 下载下来解压后，进入根目录

- 复制或修改`config`目录下的`config.example.yaml`为`config.yaml`
  ，并根据文件注释进行配置。或自行参照下面设置保存为`config.yaml`并放到`config`目录下
    ```yaml
      bot:
        qq:
          account: 12354547  # qq账号
          password: your-qq-password # qq密码，ANDROID_WATCH 协议下
          protocol: ANDROID_PAD #ANDROID_PAD | ANDROID_PHONE | ANDROID_WATCH
        telegram:
          token: 1234567:adgcgasdfadf # tg机器人token
          username: your_bot_name  # tg机器人用户名
        tg-msg-format: "#$name #id$id $newline$newline$msg" #tg消息格式化: $name: 发送者名称；$id: 发送者id；$newline: 换行；$msg: 消息体
        qq-msg-format: "#$name: $msg" # qq消息格式化
        master-of-tg: 12345678 # 主人 tg id
        master-of-qq: 87654321 # 主人 qq 账号
        pic-to-file-size: 2   # 图片大约多少M则转为文件发送
        enable-recall: false  # true 则删掉转发的tg消息，否则会修改成划线消息以表示撤销
      redis:
        host: redis # redis ip ；若是使用docker-compose构建环境则不需要修改
        port: 6379  # redis 端口，一般默认就这个端口；若是使用docker-compose构建环境则不需要修改
        database: 10 # redis数据库id，一般不需要修改，只有多个程序共用时为了避免问题而分开不同数据库
    ```
- jar包运行方式  
  单独jar包运行需要自己搭建环境，要求要安装java 17、redis、ffmpeg、dwebp。  
  java，redis是必须，但版本号可以稍低，其他两个则不一定需要但是会报错误。  
  上述软件具体安装这里不做赘述。
    1. 准备好环境后，在[Release](https://github.com/KurenaiRyu/im-sync-bot/releases)
       下载最新版本zip，解压后，直接运行`java -jar im-sync-bot.jar`
- docker运行方式  
  首先需要准备 [docker](https://docs.docker.com/get-docker/)
  以及 [docker-compose](https://docs.docker.com/compose/install/)
    1. 启动redis `docker compose up -d redis`, 如果用的是本地api则`docker compose up -d redis api`。
       启动bot `docker compose run -T --rm bot`
       等待运行。注意有些ssh工具比如idea是可能展示不全转行的文字，所以可能导致复制的链接刷不出二维码来（可以尝试直接点击链接而不是复制，idea貌似能够识别完全整个链接）
       如果碰到登录验证问题按照提示输入，具体看mirai官方文档， 登录成功后即可`ctrl + C`
       中断运行（也许需要多次或者关不掉，只能关闭当前ssh，手动停止镜像了，但这个貌似有时候会失败，所以记得`docker ps -a`
       看一下，删掉那些名字带了一个hash后缀的容器）。  
       想要跳过则需要把 [Mirai](https://github.com/mamoe/mirai) 生成的device.json文件放入config目录下。
    2. 输入命令 `docker-compose up -d` 后台运行程序

随后发送`/help`查看信息，例如在一个tg的群聊当中发送`/bind xxxx`进行绑定xxxxQ群，xxxx为Q群号。
> tips  
> /recall命令由于会删除用户信息，故需要管理员权限，但qq那边的撤回不受影响。

其他会用到的命令

- `docker-compose logs -f --tail 500 bot` 看机器人500行日志
- `docker-compose stop` 停止
- `docker-compose restart` 重启
- `docker-compose up -d` 更新docker-compose配置后台运行

## Backup

只需要备份`config`目录下的文件即可，如果还需要缓存的信息，则还需要备份`redis-data`，但缓存文件有可能会有权限问题。

## Development

由于我使用了自己写的[缓存工具](https://github.com/KurenaiRyu/simple-cache.git) 和 [telegram sdk](https://github.com/KurenaiRyu/tdlight-sdk)，所以请到对应项目下载源码编译安装至本地maven仓库。

## Thanks

[Mirai](https://github.com/mamoe/mirai)  
[TelegramBots](https://github.com/rubenlagus/TelegramBots)  