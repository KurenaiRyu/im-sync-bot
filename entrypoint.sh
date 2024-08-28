#!/bin/bash
chmod +rwx /app/*.yaml && chmod +rwx /app/data/* && java -jar -Xms150m -Xmx300m /app/im-sync-bot.jar -Dkotlinx.coroutines.debug