package com.ssafy.wswg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WswgApplication {

	public static void main(String[] args) {
		SpringApplication.run(WswgApplication.class, args);
	}

}
