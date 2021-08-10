# IM-SYNC-BOT

im同步机器人，现主要同步（转发）tg跟qq群。

## Quickstart

首先需要准备 [docker](https://docs.docker.com/get-docker/) 以及 [docker-compose](https://docs.docker.com/compose/install/)

把 [项目源码](https://github.com/KurenaiRyu/im-sync-bot/releases) 下载下来解压后，进入根目录

1. 复制或修改`config.example.yaml`为`config.yaml`，并根据文件注释进行配置。  
   最简配置：
    ```yaml
    bot:
      telegram:
        token: 1234567:adgcgasdfadf # tg机器人token
        username: your_bot_name  # tg机器人用户名
      #    proxy:
      #      host: 127.0.0.1    # tg代理地址
      #      port: 1080
      #      type: socks5   # socks5 | http
      qq:
        account: # qq账号
        password: # qq密码
        protocol: ANDROID_PAD # 登录协议  ANDROID_PAD | ANDROID_PHONE | ANDROID_WATCH
      handler:
        forward:
          group:
            qq-telegram: 
              # qq-tg对应关系（一对一）
              1234546768: -12345435646576
              1176542133: -9484733652578
    logging:
      file:
        name: ./log/im-sync.log
    ```
2. 输入命令 `docker-compose up --build` 等待编译并运行。如果碰到登录验证问题按照提示输入， 查看到生成device.json文件后，即可ctrl + C中断运行。  
   想要跳过则需要把 [Mirai](https://github.com/mamoe/mirai) 生成的device.json文件放入根目录下。
3. 输入命令 `docker-compose up -d` 后台运行程序

其他会用到的命令

- `docker-compose logs -f` 看日志
- `docker-compose stop` 停止
- `docker-compose restart` 重启
- `docker-compose up -d` 更新docker-compose配置后台运行

## Develop

复制 `config.example.yaml` 到 `src/main/resources` 并更改名字为 `application.yaml` 。  
本项目也采用了Lombok，所以也请注意安装对应插件。

## Thanks

[Mirai](https://github.com/mamoe/mirai)  
[TelegramBots](https://github.com/rubenlagus/TelegramBots)  