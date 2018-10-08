package eu.h2020.symbiote.ele;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import eu.h2020.symbiote.enablerlogic.WaitForPort;

/**
 * @author Mario Kušek
 *
 */
@SpringBootApplication
@EnableDiscoveryClient
public class EnablerLogicExample {

    public static void main(String[] args) {
        WaitForPort.waitForServices(WaitForPort.findProperty("SPRING_BOOT_WAIT_FOR_SERVICES"));
		SpringApplication.run(EnablerLogicExample.class, args);
	}
}
