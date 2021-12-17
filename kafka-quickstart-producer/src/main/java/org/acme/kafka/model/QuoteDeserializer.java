package org.acme.kafka.model;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/**
 * @Autor Jairo Nascimento
 * @Created 17/12/2021 - 08:19
 */
public class QuoteDeserializer extends ObjectMapperDeserializer<Quote> {
    public QuoteDeserializer() {
        super(Quote.class);
    }
}
