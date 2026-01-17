package kurenai.imsyncbot

import io.vertx.core.AbstractVerticle
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.StaticHandler
import kurenai.imsyncbot.service.HttpFileService
import kurenai.imsyncbot.utils.getLogger

class WebApplication : AbstractVerticle() {
    private val log = getLogger()

    override fun start() {
        val router = Router.router(kurenai.imsyncbot.vertx)
        router.route().handler(BodyHandler.create())
        router.getWithRegex("/file/.*").handler(HttpFileService::retrieveFile)
        router.get("/health").handler { ctx ->
            ctx.response().setStatusCode(200).end("OK")
        }
        router.get().handler(StaticHandler.create())

        vertx
            .createHttpServer()
            .requestHandler(router::handle)
            .listen(configProperties.bot.fileServer.port)
            .andThen { server, throwable ->
                if (throwable != null) {
                    log.error(throwable.localizedMessage, throwable)
                } else {
                    log.info("Web server listen to {}", server.actualPort())
                }
            }
    }

}