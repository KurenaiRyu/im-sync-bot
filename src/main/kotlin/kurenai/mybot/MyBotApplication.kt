package kurenai.mybot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MyBotApplication

fun main(args: Array<String>) {
    runApplication<MyBotApplication>(*args)
}