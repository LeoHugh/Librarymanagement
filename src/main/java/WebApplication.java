import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import utils.ConnectConfig;
import utils.DatabaseConnector;

@SpringBootConfiguration
@EnableAutoConfiguration
public class WebApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }

    @Bean(destroyMethod = "release")
    public DatabaseConnector databaseConnector() throws Exception {
        ConnectConfig config = new ConnectConfig();
        DatabaseConnector connector = new DatabaseConnector(config);
        if (!connector.connect()) {
            throw new IllegalStateException("Failed to connect database");
        }
        return connector;
    }

    @Bean
    public LibraryManagementSystem libraryManagementSystem(DatabaseConnector connector) {
        return new LibraryManagementSystemImpl(connector);
    }

    @Bean
    public LibraryApiController libraryApiController(LibraryManagementSystem library,
                                                     DatabaseConnector connector) {
        return new LibraryApiController(library, connector);
    }

   /*  @Bean
    public CommandLineRunner initDatabase(LibraryManagementSystem library) {
        return args -> library.resetDatabase();
    }*/

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
