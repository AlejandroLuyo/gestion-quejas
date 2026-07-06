package com.cibertec.gestion_quejas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GestionQuejasApplication {

	public static void main(String[] args) {
		SpringApplication.run(GestionQuejasApplication.class, args);
	}
}