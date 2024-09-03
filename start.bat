@echo off
chcp 65001
title im-sync-bot
java -Xms150m -Xmx300m -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dconsole.encoding=UTF-8 -jar im-sync-bot.jar