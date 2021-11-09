package org.domnikl.gaia

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.ext.web.Router
import io.vertx.micrometer.PrometheusScrapingHandler

class WebVerticle: AbstractVerticle() {
    override fun start(startPromise: Promise<Void>) {
        val router: Router = Router.router(vertx)

        router.route("/metrics").handler(PrometheusScrapingHandler.create())

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(8888);
    }
}
