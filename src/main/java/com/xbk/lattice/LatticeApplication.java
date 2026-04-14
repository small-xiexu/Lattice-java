package com.xbk.lattice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LatticeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LatticeApplication.class, args);
    }
}
