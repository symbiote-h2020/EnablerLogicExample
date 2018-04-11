package eu.h2020.symbiote.ele;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @author Mario Kušek
 *
 */
@SpringBootApplication
@EnableDiscoveryClient
public class EnablerLogicExample {

    public static void main(String[] args) {
		SpringApplication.run(EnablerLogicExample.class, args);
	}
}
