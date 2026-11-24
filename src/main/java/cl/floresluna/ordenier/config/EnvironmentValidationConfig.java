package cl.floresluna.ordenier.config;

import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Valida las cuatro variables de entorno antes de crear cualquier bean (spec 008, R15 a R18),
 * es decir, antes de conectarse a la base de datos. Lee las propiedades ya resueltas, así las
 * pruebas que fijan sus propios valores no dependen del entorno del sistema (R14).
 */
@Configuration(proxyBeanMethods = false)
public class EnvironmentValidationConfig {

	@Bean
	static BeanFactoryPostProcessor environmentValidator(Environment environment) {
		return beanFactory -> {
			List<String> problems = EnvironmentSettingsChecker.check(
					environment.getProperty("spring.datasource.url"),
					environment.getProperty("spring.datasource.username"),
					environment.getProperty("spring.datasource.password"),
					environment.getProperty("app.jwt.secret"));
			if (!problems.isEmpty()) {
				throw new EnvironmentConfigurationException(problems);
			}
		};
	}
}
