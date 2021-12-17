package org.acme.kafka.model;

/**
 * @Autor Jairo Nascimento
 * @Created 16/12/2021 - 19:00
 */
public class Quote {
    public String id;
    public int price;

    /**
     * Default constructor required for Jackson serializer
     */
    public Quote() { }

    public Quote(String id, int price) {
        this.id = id;
        this.price = price;
    }

    @Override
    public String toString() {
        return "Quote{" +
                "id='" + id + '\'' +
                ", price=" + price +
                '}';
    }
}
