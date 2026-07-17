package com.ssc.booking;

import com.ssc.booking.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class SscBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SscBookingApplication.class, args);
    }
}
