# IM-SYNC-BOT

im同步机器人，现主要同步（转发）tg跟qq群。

## Quickstart

首先需要准备 [docker](https://docs.docker.com/get-docker/) 以及 [docker-compose](https://docs.docker.com/compose/install/)

把 [项目源码](https://github.com/KurenaiRyu/im-sync-bot/releases) 下载下来解压后，进入根目录

1. 复制或修改`config`目录下的`config.example.yaml`为`config.yaml`，并根据文件注释进行配置。或自行参照下面设置保存为`config.yaml`并放到`config`目录下
    ```yaml
    spring:
      redis:
        host: ${REDIS_HOST:localhost}
        port: ${REDIS_PORT:6379}
    bot:
      telegram:
        token: 1234567:adgcgasdfadf # tg机器人token
        username: your_bot_name  # tg机器人用户名
    qq:
      account: 12354547  # qq账号
      password: your-qq-password # qq密码
      protocol: ANDROID_PAD #ANDROID_PAD | ANDROID_PHONE | ANDROID_WATCH
    handler:
      forward:
        enable: true  # 开启转发（就算删除也默认开启）
        master-of-tg: 12345678 #主人 tg id
        master-of-qq: 87654321 #主人 qq 账号
        tgMsgFormat: "$name$newline$newline$msg"  #tg消息格式化
        qqMsgFormat: "$name: $msg" # qq消息格式化
    
    logging:
      file:
        name: ./log/im-sync-bot.log
    ```
2. 如果你有之前qq机器人生成过得 `device.json`，则只需要把他放入项目根目录下然后进行下面的第3步即可。没有则如下操作：  
   输入命令 `docker-compose up bot` 等待运行。如果碰到登录验证问题按照提示输入， 查看到生成device.json文件后，即可`ctrl + C`中断运行。  
   想要跳过则需要把 [Mirai](https://github.com/mamoe/mirai) 生成的device.json文件放入根目录下。
3. 输入命令 `docker-compose up -d` 后台运行程序

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

复制 `config.example.yaml` 到 `src/main/resources` 并更改名字为 `application.yaml` 。

由于我使用了自己写的[缓存工具](https://github.com/KurenaiRyu/simple-cache.git) 和 [telegram sdk](https://github.com/KurenaiRyu/tdlight-sdk)，所以请到对应项目下载源码编译安装至本地maven仓库。

## Thanks

[Mirai](https://github.com/mamoe/mirai)  
[TelegramBots](https://github.com/rubenlagus/TelegramBots)  