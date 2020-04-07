package kafka_clients.twitterClientKProducer;

import com.google.common.collect.Lists;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TwitterProducerService {
    static Logger logger = LoggerFactory.getLogger(TwitterProducerService.class.getName());
    Client client;
    KafkaProducer<String, String> producer;
    BlockingQueue<String> msgQueue;
    public TwitterProducerService(){
        setup();
    }

    public void setup(){
        logger.info("Setup");

        //Setup your blocking queues
        msgQueue = new LinkedBlockingQueue<String>(100000);

        //Create a Twitter Client
        client = createTwitterClient(msgQueue);
        //Attempts to establish a connection
        client.connect();

        //Create a kafka producer
        producer = createKafkaProducer(client);

        //add a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("stopping application...");
            logger.info("shutting down client from twitter...");
            client.stop();
            logger.info("closing producer...");
            producer.close();
            logger.info("DOne!");
        }));
    }

    public void run(){
        //loop to send tweet to Kafka
        //on a different thread, or multiple different threads...
        while(!client.isDone()){
            String msg = null;
            try{
                msg = msgQueue.poll(5, TimeUnit.SECONDS);
            }
            catch (Exception e){
                e.printStackTrace();
                client.stop();
            }
            if(msg!=null){
                logger.info(msg);
                send(msg);
            }
        }
        logger.info("End of the Application!");
    }

    public void send(String msg){
        producer.send(new ProducerRecord<String, String>("twitter_tweets", null, msg), new Callback() {
            public void onCompletion(RecordMetadata recordMetadata, Exception e) {
                if(e != null){
                    logger.error("Something bad happened", e);
                }
            }
        });
    }

    static String consumerKey = "9p99pL7ylcQ56RYb2SVsQV5vA";
    static String consumerSecret = "Emq3dcRqeLR20KMPhEFyStyjBA8VrV1ZdxltTQIasJ07bKaNd8";
    static String token = "1240605694409261056-UjjrHVfYMTjiWniXUmuCsiXazBCdRZ";
    static String secret = "Y2ChO9sqCs0UttgWWfqz7nZrUtGzDfbIyA6ypy7SqFbx6";

    public static Client createTwitterClient(BlockingQueue<String> msgQueue){
        //Declare the host that you want to connect to, the endpoint, the authentication (basic or oauth)
        Hosts hosebirdHosts = new HttpHosts(Constants.STREAM_HOST);
        StatusesFilterEndpoint hosebirdEndpoint = new StatusesFilterEndpoint();
        //Optional: set up some followings and track terms
        List<String> terms = Lists.newArrayList("coronavirus", "trump", "biden", "sanders");
        hosebirdEndpoint.trackTerms(terms);

        //These secrets should be read from a config file
        Authentication hosebirdAuthentication = new OAuth1(
                consumerKey,
                consumerSecret,
                token,
                secret
        );

        ClientBuilder builder = new ClientBuilder()
                .name("Hosebird-client-01")
                .hosts(hosebirdHosts)
                .authentication(hosebirdAuthentication)
                .endpoint(hosebirdEndpoint)
                .processor(new StringDelimitedProcessor(msgQueue));
        Client hosebirdClient = builder.build();
        return hosebirdClient;
    }

    public static KafkaProducer<String, String> createKafkaProducer(Client client){
        String bootstrapServers = "localhost:9092";

        //create Producer   Properties
        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        //create safe producer
        properties.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        properties.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        properties.setProperty(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));
        properties.setProperty(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");

        //high throughput producer (at cost of a bit of latency and CPU usage)ù
        properties.setProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        properties.setProperty(ProducerConfig.LINGER_MS_CONFIG, "20");
        properties.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(32*1024)); //32kB batch size


        //create the producer
        KafkaProducer<String,String> kafkaProducer = new KafkaProducer<String, String>(properties);
        return kafkaProducer;
    }
}
