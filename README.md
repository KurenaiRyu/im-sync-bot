# IM-SYNC-BOT

![Build Status](https://github.com/KurenaiRyu/im-sync-bot/actions/workflows/build.yml/badge.svg?branch=dev)
[![codecov](https://codecov.io/gh/KurenaiRyu/im-sync-bot/branch/dev/graph/badge.svg)](https://github.com/KurenaiRyu/im-sync-bot/tree/dev)

im同步机器人，现主要同步（转发）tg跟qq群。

## 使用方式
- jar包运行方式  
  1. 单独jar包运行需要自己搭建环境，要求要安装java 17。  
  另外ffmpeg、dwebp则不一定需要但是会报错误，windows用户可以下载对应exe文件放入jar包所在的目录下即可。
  上述软件具体安装这里不做赘述。
  2. 复制项目下的`config.example.yaml`为`application.yaml`，根据文件内的注释修改配置。
  3. 准备好环境后，在[Release](https://github.com/KurenaiRyu/im-sync-bot/releases)
       下载最新版本zip，解压后和上面修改的`config.yaml`文件放在一起，直接运行`start.bat`(windows)/`start.sh`
- docker运行方式  
  首先需要准备 [docker](https://docs.docker.com/get-docker/)
  以及 [docker-compose](https://docs.docker.com/compose/install/)
    1. 复制项目中的`docker-compose.yaml`以及修改后的`config.yaml`文件到同一目录下
    2. 输入命令 `docker compose up -d` 后台运行程序
    3. `docker compose ps` 可查看状态，`docker compose logs -f` 查看日志 
- 自编译
  1. `gradlew build` 进行编译
  2. 将`config.yaml`放在根目录 
  3. `java -Xms150m -Xmx300m -jar build/libs/im-sync-bot.jar` 启动bot

随后发送`/help`查看信息，例如在一个tg的群聊当中发送`/bind xxxx`进行绑定xxxxQ群，xxxx为Q群号。
> tips  
> /recall命令由于会删除用户信息，故需要管理员权限，但qq那边的撤回不受影响。

## 迁移

迁移`im-sync-bot.db`（数据库文件），以及根目录下的配置文件`config.yaml`

## Thanks

[Mirai](https://github.com/mamoe/mirai)  
[TelegramBots](https://github.com/rubenlagus/TelegramBots)  
[Overflow](https://github.com/MrXiaoM/Overflow)
