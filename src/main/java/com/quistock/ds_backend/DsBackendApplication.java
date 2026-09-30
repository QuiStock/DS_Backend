package com.quistock.ds_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@SuppressWarnings("PMD.UseUtilityClass")
public class DsBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(DsBackendApplication.class, args);
  }
}
