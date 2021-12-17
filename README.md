# INTRODUÇÃO AO SMALLRYE REACTIVE MESSAGING COM APACHE KAFKA

Este guia demonstra como seu aplicativo Quarkus pode utilizar SmallRye Reactive Messaging para interagir com o Apache Kafka

## Pré-requisitos

Para completar este guia, você precisa:

* menos de 15 minutos

* um IDE

* JDK 11+ instalado com JAVA_HOMEconfigurado apropriadamente

* Apache Maven 3.8.1+

* Docker e docker-compose para executar o aplicativo em contêineres

## Arquitetura
Neste guia, iremos desenvolver dois aplicativos que se comunicam com o Kafka. O primeiro aplicativo envia uma solicitação de cotação para Kafka e consome mensagens Kafka do tópico de cotação . O segundo aplicativo recebe a solicitação de orçamento e envia um orçamento de volta.

![](https://quarkus.io/guides/images/kafka-qs-architecture.png)

O primeiro aplicativo, o *produtor* , permitirá que o usuário solicite algumas cotações em um endpoint HTTP. Para cada solicitação de cotação, um identificador aleatório é gerado e retornado ao usuário, para marcar a solicitação de cotação como pendente . Ao mesmo tempo, o id de solicitação gerado é enviado por meio de um tópico Kafka `quote-requests`.

![](https://quarkus.io/guides/images/kafka-qs-app-screenshot.png?style=centerme)

O segundo aplicativo, o *processador* , lerá o quote-requeststópico, colocará um preço aleatório na cotação e enviará para um tópico Kafka chamado quotes.

Por último, o *produtor* lerá as cotações e as enviará ao navegador usando eventos enviados pelo servidor. O usuário verá, portanto, o preço de cotação atualizado de pendente para o preço recebido em tempo real.

## Solução
Recomendamos que você siga as instruções nas próximas seções e crie aplicativos passo a passo. No entanto, você pode ir direto ao exemplo completo.

Clone o repositório Git: ou baixe um arquivo .git clone https://github.com/quarkusio/quarkus-quickstarts.git

A solução está localizada no *kafka-quickstart* diretório .

## Criando o Projeto Maven
Primeiro, precisamos criar dois projetos: o *produtor* e o *processador* .

Para criar o projeto *produtor* , em uma execução de terminal:

```shell script
mvn io.quarkus.platform:quarkus-maven-plugin:2.5.3.Final:create \
    -DprojectGroupId=org.acme \
    -DprojectArtifactId=kafka-quickstart-producer \
    -DnoCode=true \
    -Dextensions=resteasy-reactive-jackson,smallrye-reactive-messaging-kafka
```

Este comando cria a estrutura do projeto e seleciona duas extensões Quarkus que usaremos:

1. RESTEasy Reactive e seu suporte a Jackson (para lidar com JSON) para servir ao endpoint HTTP.

2. O conector Kafka para mensagens reativas

Para criar o projeto do *processador* , no mesmo diretório, execute:

```shell script
mvn io.quarkus.platform:quarkus-maven-plugin:2.5.3.Final:create \
    -DprojectGroupId=org.acme \
    -DprojectArtifactId=kafka-quickstart-processor \
    -DnoCode=true \
    -Dextensions=smallrye-reactive-messaging-kafka

``` 

Nesse ponto, você deve ter a seguinte estrutura:

```text
.
├── kafka-quickstart-processor
│  ├── README.md
│  ├── mvnw
│  ├── mvnw.cmd
│  ├── pom.xml
│  └── src
│     └── main
│        ├── docker
│        ├── java
│        └── resources
│           └── application.properties
└── kafka-quickstart-producer
   ├── README.md
   ├── mvnw
   ├── mvnw.cmd
   ├── pom.xml
   └── src
      └── main
         ├── docker
         ├── java
         └── resources
            └── application.properties
```

Abra os dois projetos em seu IDE favorito.

> *Dev Services*
Não há necessidade de iniciar um corretor Kafka ao usar o modo dev ou para testes. A Quarkus abre um corretor para você automaticamente. Consulte Dev Services for Kafka para obter detalhes.

## The Quote object

A classe `Quote` será usada em projetos de *produtores* e *processadores* . Por uma questão de simplicidade, iremos duplicar a classe. Em ambos os projetos, crie o arquivo `src/main/java/org/acme/kafka/model/Quote.java`, com o seguinte conteúdo:

```java
package org.acme.kafka.model;

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
```

A representação JSON do objetos `Quote` será usada em mensagens enviadas para o tópico Kafka e também nos eventos enviados pelo servidor enviados para navegadores da web

O Quarkus possui recursos integrados para lidar com mensagens JSON Kafka. Em uma seção a seguir, criaremos classes de serializador / desserializador para Jackson.

## Enviando solicitação de cotação
Dentro do projeto do produtor , crie o arquivo `src/main/java/org/acme/kafka/producer/QuotesResource.java`  e adicione o seguinte conteúdo:

```java
package org.acme.kafka.producer;

import java.util.UUID;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.acme.kafka.model.Quote;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@Path("/quotes")
public class QuotesResource {

    @Channel("quote-requests")
    Emitter<String> quoteRequestEmitter; //(1)

    /**
     * Endpoint to generate a new quote request id and send it to "quote-requests" Kafka topic using the emitter.
     */
    @POST
    @Path("/request")
    @Produces(MediaType.TEXT_PLAIN)
    public String createRequest() {
        UUID uuid = UUID.randomUUID();
        quoteRequestEmitter.send(uuid.toString()); //(2)
        return uuid.toString(); //(3)
    }
}

```

1. Injetar uma `Emitter` mensagem reativa para enviar mensagens ao canal `quote-requests`.
1. Em uma solicitação de postagem, gere um UUID aleatório e envie-o ao tópico Kafka usando o emissor.
1. Retorne o mesmo UUID para o cliente.

Precisamos instruir Quarkus para mapear o quote-requestscanal para um tópico Kafka. No arquivo `src/main/resources/application.properties` do projeto produtor, adicione:

```text
# Configure the outgoing `quote-requests` Kafka topic
mp.messaging.outgoing.quote-requests.connector=smallrye-kafka
```

Tudo o que precisamos especificar é o conector `smallrye-kafka` . Por padrão, o conector Kafka usa o nome do canal (`quote-requests`) como o nome do tópico Kafka. O Quarkus configura o serializador automaticamente, pois descobre que o `Emitter` produz `String` valores.

## Processando solicitações de cotação
Agora vamos consumir a solicitação de cotação e fornecer um preço. Dentro do projeto do *processador*, crie o  arquivo `src/main/java/org/acme/kafka/processor/QuotesProcessor.java` e adicione o seguinte conteúdo:

```java
package org.acme.kafka.processor;

import java.util.Random;

import javax.enterprise.context.ApplicationScoped;

import org.acme.kafka.model.Quote;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import io.smallrye.reactive.messaging.annotations.Blocking;

/**
 * A bean consuming data from the "quote-requests" Kafka topic (mapped to "requests" channel) and giving out a random quote.
 * The result is pushed to the "quotes" Kafka topic.
 */
@ApplicationScoped
public class QuotesProcessor {

    private Random random = new Random();

    @Incoming("requests") //(1)
    @Outgoing("quotes")  //(2)
    @Blocking           //(3)
    public Quote process(String quoteRequest) throws InterruptedException {
        // simulate some hard working task
        Thread.sleep(200);
        return new Quote(quoteRequest, random.nextInt(100));
    }
}
```

1. Indica que o método consome os itens do canal `requests`.
1. Indica que os objetos retornados pelo método são enviados ao canal `quotes`.
1. Indica que o processamento está *bloqueando* e não pode ser executado no encadeamento do chamador.

Para cada *registro* Kafka do tópico `quote-requests`, o Reactive Messaging chama o método `process`  e envia o objeto `Quote` retornado ao canal `quotes`. Como no exemplo anterior, precisamos configurar os conectores no arquivo `application.properties` para mapear os canais requests e quotes para os tópicos Kafka:

```text
%dev.quarkus.http.port=8081

# Configure the incoming `quote-requests` Kafka topic
mp.messaging.incoming.requests.connector=smallrye-kafka
mp.messaging.incoming.requests.topic=quote-requests
mp.messaging.incoming.requests.auto.offset.reset=earliest

# Configure the outgoing `quotes` Kafka topic
mp.messaging.outgoing.quotes.connector=smallrye-kafka
mp.messaging.outgoing.quotes.value.serializer=io.quarkus.kafka.client.serialization.ObjectMapperSerializer
```

Observe que, neste caso, temos uma configuração de conector de entrada e uma de saída, cada uma com nomes distintos. As chaves de configuração são estruturadas da seguinte forma:


`mp.messaging.[outgoing|incoming].{channel-name}.property=value`

O segmento `channel-name` deve corresponder ao valor definido na anotação `@Incominge` `@Outgoing`:

* `quote-requests` → Tópico Kafka a partir do qual lemos os pedidos de orçamento

* `quotes` → Tópico Kafka em que escrevemos as citações

> Mais detalhes sobre essa configuração estão disponíveis na seção Configuração do produtor e Configuração do consumidor na documentação do Kafka. Essas propriedades são configuradas com o prefixo kafka. Uma lista exaustiva de propriedades de configuração está disponível no [Guia de Referência Kafka - Configuração](https://quarkus.io/guides/kafka#kafka-configuration).


Além disso, para a configuração de saída, especificamos o serializador porque estamos enviando um Quoteobjeto como a carga útil da mensagem.

O Quarkus fornece implementações padrão para pares serializador/desserializador Kafka usando Jackson `ObjectMapper.ObjectMapperSerializer` pode ser usado para serializar todos os objetos via Jackson.

## Recebendo orçamentos

De volta ao nosso projeto de *produtor*. Vamos modificar o `QuotesResource` para consumir cotações de Kafka e enviá-las de volta ao cliente por meio de eventos enviados pelo servidor:

```Java
import io.smallrye.mutiny.Multi;

...

@Channel("quotes")
Multi<Quote> quotes; //(1)

/**
 * Endpoint retrieving the "quotes" Kafka topic and sending the items to a server sent event.
 */
@GET
@Produces(MediaType.SERVER_SENT_EVENTS) //(2)
public Multi<Quote> stream() {
    return quotes; //(3)
}
```

1. Injeta o quotescanal usando o qualificador @Channel
1. Indica que o conteúdo é enviado usando `Server Sent Events`
1. Retorna o stream ( *Reactive Stream* )


Novamente, precisamos configurar o canal quotes de entrada dentro do projeto *produtor*. Adicione o seguinte no arquivo interno `application.properties`:

```text
# Configure the incoming `quotes` Kafka topic
mp.messaging.incoming.quotes.connector=smallrye-kafka
```

> Neste guia, exploramos a estrutura *Smallrye Reactive Messaging* para interagir com o Apache Kafka. A extensão Quarkus para Kafka também permite [usar clientes Kafka diretamente](https://quarkus.io/guides/kafka#kafka-bare-clients).

## Serialização JSON via Jackson
Por fim, configuraremos a serialização JSON para mensagens usando Jackson. Anteriormente, vimos o uso de `ObjectMapperSerializerpara` serializar objetos via Jackson. Para a classe desserializadora correspondente, precisamos criar uma subclasse de `ObjectMapperDeserializer`.

Então, vamos criá-lo dentro do projeto produtor em `src/main/java/org/acme/kafka/model/QuoteDeserializer.java`

```java
package org.acme.kafka.model;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class QuoteDeserializer extends ObjectMapperDeserializer<Quote> {
    public QuoteDeserializer() {
        super(Quote.class);
    }
}
```

Não há necessidade de adicionar qualquer configuração para este arquivo interno `application.properties`. O Quarkus detectará automaticamente este desserializador.

> Serialização de mensagens em Kafka
Neste exemplo, usamos Jackson para serializar/desserializar mensagens Kafka. Para obter mais opções sobre a serialização de mensagens, consulte [o Guia de Referência Kafka - Serialização](https://quarkus.io/guides/kafka#kafka-serialization).

> É altamente recomendável adotar uma abordagem de primeiro contrato usando um registro de esquema. Para saber mais sobre como usar o Apache Kafka com o registro de esquema e **Avro**, siga [o guia Usando Apache Kafka com Registro de esquema e Avro](https://quarkus.io/guides/kafka-schema-registry-avro).

## A página HTML

Toque final, a página HTML de solicitação de orçamentos e exibindo os preços obtidos no SSE.

Dentro do projeto do *produtor* , crie o arquivo `src/main/resources/META-INF/resources/quotes.html` com o seguinte conteúdo:

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Prices</title>

    <link rel="stylesheet" type="text/css"
          href="https://cdnjs.cloudflare.com/ajax/libs/patternfly/3.24.0/css/patternfly.min.css">
    <link rel="stylesheet" type="text/css"
          href="https://cdnjs.cloudflare.com/ajax/libs/patternfly/3.24.0/css/patternfly-additions.min.css">
</head>
<body>
<div class="container">
    <div class="card">
        <div class="card-body">
            <h2 class="card-title">Quotes</h2>
            <button class="btn btn-info" id="request-quote">Request Quote</button>
            <div class="quotes"></div>
        </div>
    </div>
</div>
</body>
<script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
<script>
    $("#request-quote").click((event) => {
        fetch("/quotes/request", {method: "POST"})
        .then(res => res.text())
        .then(qid => {
            var row = $(`<h4 class='col-md-12' id='${qid}'>Quote # <i>${qid}</i> | <strong>Pending</strong></h4>`);
            $(".quotes").prepend(row);
        });
    });

    var source = new EventSource("/quotes");
    source.onmessage = (event) => {
      var json = JSON.parse(event.data);
      $(`#${json.id}`).html((index, html) => {
        return html.replace("Pending", `\$\xA0${json.price}`);
      });
    };
</script>
</html>
```

Nada de espetacular aqui. Quando o usuário clica no botão, uma solicitação HTTP é feita para solicitar uma cotação e uma cotação pendente é adicionada à lista. Em cada cotação recebida através de SSE, o item correspondente na lista é atualizado.

## Faça funcionar

Você só precisa executar os dois aplicativos. Em um terminal, execute:

```shell script
mvn -f kafka-quickstart-producer quarkus:dev
```
Em outro terminal, execute:
```shell script
mvn -f kafka-quickstart-processor quarkus:dev
```

O Quarkus inicia um corretor Kafka automaticamente, configura o aplicativo e compartilha a instância do corretor Kafka entre diferentes aplicativos. Consulte [Dev Services for Kafka](https://quarkus.io/guides/kafka-dev-services) para obter mais detalhes.

Abra [http://localhost:8080/quotes.html](http://localhost:8080/quotes.html) no seu navegador e solicite alguns orçamentos clicando no botão.

## Executando em JVM ou modo nativo

Quando não estiver executando em modo de desenvolvimento ou teste, você precisará iniciar seu corretor Kafka. Você pode seguir as instruções do site do Apache Kafka ou criar um `docker-compose.yaml` arquivo com o seguinte conteúdo:

```shell script
version: '3.5'
services:

  zookeeper:
    image: quay.io/strimzi/kafka:0.23.0-kafka-2.8.0
    command: [
      "sh", "-c",
      "bin/zookeeper-server-start.sh config/zookeeper.properties"
    ]
    ports:
      - "2181:2181"
    environment:
      LOG_DIR: /tmp/logs
    networks:
      - kafka-quickstart-network

  kafka:
    image: quay.io/strimzi/kafka:0.23.0-kafka-2.8.0
    command: [
      "sh", "-c",
      "bin/kafka-server-start.sh config/server.properties --override listeners=$${KAFKA_LISTENERS} --override advertised.listeners=$${KAFKA_ADVERTISED_LISTENERS} --override zookeeper.connect=$${KAFKA_ZOOKEEPER_CONNECT}"
    ]
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      LOG_DIR: "/tmp/logs"
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
    networks:
      - kafka-quickstart-network

  producer:
    image: quarkus-quickstarts/kafka-quickstart-producer:1.0-${QUARKUS_MODE:-jvm}
    build:
      context: producer
      dockerfile: src/main/docker/Dockerfile.${QUARKUS_MODE:-jvm}
    depends_on:
      - kafka
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    ports:
      - "8080:8080"
    networks:
      - kafka-quickstart-network

  processor:
    image: quarkus-quickstarts/kafka-quickstart-processor:1.0-${QUARKUS_MODE:-jvm}
    build:
      context: processor
      dockerfile: src/main/docker/Dockerfile.${QUARKUS_MODE:-jvm}
    depends_on:
      - kafka
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    networks:
      - kafka-quickstart-network

networks:
  kafka-quickstart-network:
    name: kafkaquickstart

```

Certifique-se de criar primeiro ambos os aplicativos no modo JVM com:

```shell script
mvn -f kafka-quickstart-producer package
mvn -f kafka-quickstart-processor package
```

Depois de embalado, execute docker-compose up.

> :information_source: | Este é um cluster de desenvolvimento, não use na produção.

Você também pode construir e executar nossos aplicativos como executáveis ​​nativos. Primeiro, compile os dois aplicativos como nativos:


```shell script
mvn -f kafka-quickstart-producer package -Dnative -Dquarkus.native.container-build=true
mvn -f kafka-quickstart-processor package -Dnative -Dquarkus.native.container-build=true
```

Execute o sistema com:
```shell script
export QUARKUS_MODE=native
docker-compose up --build
```

## Indo além

Este guia mostrou como você pode interagir com Kafka usando o Quarkus. Ele utiliza [SmallRye Reactive Messaging](https://smallrye.io/smallrye-reactive-messaging) para construir aplicativos de streaming de dados.

Para obter a lista completa de recursos e opções de configuração, verifique [o Guia de referência da extensão Apache Kafka](https://quarkus.io/guides/kafka).

