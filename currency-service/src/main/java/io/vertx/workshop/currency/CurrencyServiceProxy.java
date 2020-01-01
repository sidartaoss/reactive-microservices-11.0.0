package io.vertx.workshop.currency;

import io.reactivex.Single;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;
import io.vertx.reactivex.servicediscovery.types.EventBusService;
import io.vertx.reactivex.servicediscovery.types.HttpEndpoint;
import io.vertx.workshop.portfolio.reactivex.PortfolioService;

import static io.vertx.workshop.currency.Helpers.toObserver;

/**
 * Verticle acting as proxy between our application and the flaky 3rd party currency service.
 */
public class CurrencyServiceProxy extends AbstractVerticle {

    private CircuitBreaker circuit;
    private ServiceDiscovery discovery;

    @Override
    public void start() throws Exception {
        System.out.println("\n\nEntering in method start");
        Router router = Router.router(vertx);

        router.get().handler(this::convertPortfolioToEuro);

        router.post().handler(BodyHandler.create());

        router.post().handler(this::delegateWithCircuitBreaker);

        circuit = CircuitBreaker.create("circuit-breaker", vertx,
            new CircuitBreakerOptions()
                .setFallbackOnFailure(true)
                .setMaxFailures(3)
                .setResetTimeout(5000)
                .setTimeout(1000)        
        );

        discovery = ServiceDiscovery.create(vertx);

        vertx.createHttpServer().requestHandler(router).listen(8087);
    }

    private void delegateWithCircuitBreaker(RoutingContext rc) {
        System.out.println("\n\nEntering in method delegateWithCircuitBreaker");
        HttpEndpoint.rxGetWebClient(discovery, rec -> rec.getName().equals("currency-3rdparty"))
                .flatMap(client ->

                    // TODO
                    // Use the Circuit Breaker (circuit) to call the service.
                    // Use the rxExecuteCommandWithFallback method.
                    // This method takes 2 parameters: the first one is a function taking a "Future" as parameter and needs
                    // to report the success or failure on this future. The second method is a function providing the fallback 
                    // result. You must provide a JSON object as response. For the fallback use:
                    // new JsonObject()
                    //      .put("amount", rc.getBodyAsJson().getDouble("amount"))
                    //      .put("currency", "USD")
                    // In the first function, use the given client, emit a POST request on / containing the incoming payload
                    // (rc.getBodyAsJson()). Extract the response payload as JSON (bodyAsJsonObject). Don't forget to subscribe
                    // (you can use subscribe(toObserver(fut)). You can have a look to the "delegate" method as example.
                    circuit.rxExecuteCommandWithFallback(
                        fut -> 
                            client.post("/").rxSendJsonObject(rc.getBodyAsJson())
                                .map(HttpResponse::bodyAsJsonObject)
                                .subscribe(toObserver(fut))
                            ,
                        err -> 
                            new JsonObject()
                            .put("amount", rc.getBodyAsJson().getDouble("amount"))
                            .put("currency", "USD")
                    )
                )
                // ---
                .map(JsonObject::toBuffer)
                .map(Buffer::new)

                .subscribe(toObserver(rc));
                System.out.println("\n\nExiting method delegateWithCircuitBreaker");
    }

    /**
     * Example of method not using a circuit breaker
     */
    private void delegate(RoutingContext rc) {
        HttpEndpoint.rxGetWebClient(discovery, 
            rec -> rec.getName().equals("currency-3rdparty"))
        .flatMap(client -> client.post("/").rxSendJsonObject(rc.getBodyAsJson()))
        .map(HttpResponse::bodyAsBuffer)
        .subscribe(toObserver(rc));
    }

    /**
     * Method to check the proxy requesting to convert the current porfolio to EUR.
     * 
     * @param rc the routing context
     */
    private void convertPortfolioToEuro(RoutingContext rc) {
        System.out.println("\n\nEntering in method convertPortfolioToEuro");
        System.out.println("\n\nCalling method getServiceProxy from the EventBusService");
        EventBusService.getServiceProxy(discovery,
            rec -> rec.getName().equals("portfolio"),
            PortfolioService.class,
            ar -> {
                if (ar.failed()) {
                    System.err.println("\n\nError in calling portfolio");
                    rc.fail(ar.cause());
                } else {
                    System.out.println("\n\nSuccess in calling portfolio");
                    ar.result().evaluate(res -> {
                        if (res.failed()) {
                            System.err.println("\n\nError in result from the portfolio.evaluate()");
                            rc.fail(res.cause());
                            if (res.cause() != null) {
                                System.err.println("\n\nThe error is:\n\n");
                                res.cause().printStackTrace();
                            }
                        } else {
                            System.out.println("\n\nSuccess in result from the portfolio.evaluate()");
                            JsonObject payload = new JsonObject().put("amount", res.result()).put("currency", "EUR");
                            rc.setBody(new Buffer(payload.toBuffer()));
                            delegateWithCircuitBreaker(rc);
                        }
                    });
                }
            });
    }

}
