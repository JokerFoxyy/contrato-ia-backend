package br.com.contratoai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ContratoIaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContratoIaApplication.class, args);
    }
}
