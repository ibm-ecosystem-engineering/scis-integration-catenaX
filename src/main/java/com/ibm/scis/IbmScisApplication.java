package com.ibm.scis;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableAspectJAutoProxy
@ComponentScan(basePackages = "com.ibm.scis.*")
public class IbmScisApplication {

	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.configure().filename("environment_variables.env").load();
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
		SpringApplication.run(IbmScisApplication.class, args);
	}

}
