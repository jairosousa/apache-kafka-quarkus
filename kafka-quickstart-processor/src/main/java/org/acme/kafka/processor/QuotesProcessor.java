package org.acme.kafka.processor;

import io.smallrye.reactive.messaging.annotations.Blocking;
import org.acme.kafka.model.Quote;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import javax.enterprise.context.ApplicationScoped;
import java.util.Random;

/**
 * @Autor Jairo Nascimento
 * @Created 16/12/2021 - 19:14
 */
@ApplicationScoped
public class QuotesProcessor {
    private Random random = new Random();

    @Incoming("requests")
    @Outgoing("quotes")
    @Blocking
    public Quote process(String quoteRequest) throws InterruptedException {
        // simulate some hard working task
        Thread.sleep(200);
        return new Quote(quoteRequest, random.nextInt(100));
    }
}
