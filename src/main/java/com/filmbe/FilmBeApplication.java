package com.filmbe;

import com.filmbe.config.AppProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class FilmBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(FilmBeApplication.class, args);
    }

}
