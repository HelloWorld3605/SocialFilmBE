package com.filmbe;

import com.filmbe.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class FilmBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(FilmBeApplication.class, args);
    }

}
