package cl.floresluna.ordenier;

import org.springframework.boot.SpringApplication;

public class TestOrdenierApplication {

	public static void main(String[] args) {
		SpringApplication.from(OrdenierApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
