package io.vertx.workshop.currency;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.types.HttpEndpoint;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.Random;

public class CurrencyService extends AbstractVerticle {

    private Random random = new Random();

    @Override
    public void start(Future<Void> future) throws Exception {
        System.out.println("\n\nEntering in method start");

        ServiceDiscovery discovery = ServiceDiscovery.create(vertx);

        Record record = HttpEndpoint.createRecord("currency-3rdparty", 
                "currency-3rdparty-service-reactive-microservices.sidartasilva.io");
        discovery.publish(record, ar -> {
            if (ar.succeeded()) {
            // publication succeeded
            System.out.println("\n\nPublication of currency currency-3rdparty succeeded.");
            } else {
            // publication failed
            System.err.println("\n\nPublication of currency currency-3rdparty failed.");
            }
        });

        Router router = Router.router(vertx);

        router.get().handler(rc -> rc.response().end("Ok"));    

        router.post().handler(BodyHandler.create());

        router.post().handler(this::handle);

        vertx.createHttpServer().requestHandler(router).listen(8086);

    }

    private void handle(RoutingContext rc) {
        System.out.println("\n\nEntering in method handle");
        @Nullable JsonObject json = rc.getBodyAsJson();
        if (json == null || json.getDouble("amount") == null) {
            System.err.println("\n\nNo content or no amount");
            rc.fail(400);
            return;
        }

        double amount = json.getDouble("amount");
        String target = json.getString("currency");
        if (target == null) {
            target = "EUR";
        }

        double rate = getRate(target);

        if (rate == -1) {

            System.err.println("\n\nUnknown currency: " + target);
            rc.fail(400);
            return;
        }

        int i = random.nextInt(10);

        if (i < 5) {
            System.out.println("\n\nSuccess.");
            rc.response().end(new JsonObject()
                .put("amount", convert(amount, rate))
                .put("currency", target).encode()
            );
        } else if (i < 8) {
            System.err.println("\n\nFailure 500.");
            // Failure
            rc.fail(500);
        } else {
            // Timeout, we don't write the response.
            System.err.println("\n\nTimeout, we don't write the response.");
        }

        

    }

    private double getRate(String target) {
        if (target.equalsIgnoreCase("USD")) {
            System.out.println("\n\ngetRate: USD - return 1.");
            return 1;
        }

        if (target.equalsIgnoreCase("EUR")) {
            System.out.println("\n\ngetRate: EUR - return .84");
            return 0.84;
        }

        if (target.equalsIgnoreCase("GBP")) {
            System.out.println("\n\ngetRate: GBP - return .77");
            return 0.77;
        }

        System.err.println("\n\ngetRate: return -1.");
        return -1;

    }

    private double convert(double amount, double rate) {
        return amount * rate;
    }


}