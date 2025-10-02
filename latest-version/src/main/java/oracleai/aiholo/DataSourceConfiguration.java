package oracleai.aiholo;

import oracle.jdbc.pool.OracleDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Configuration
public class DataSourceConfiguration {

    @Bean
    public DataSource dataSource() throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();
        
        // Get database configuration from environment variables
        String dbUser = System.getenv("DB_USER");
        String dbPassword = System.getenv("DB_PASSWORD");
        String dbUrl = System.getenv("DB_URL");
        
        if (dbUser == null || dbPassword == null || dbUrl == null) {
            throw new RuntimeException("Database configuration missing. Please set DB_USER, DB_PASSWORD, and DB_URL environment variables.");
        }
        
        dataSource.setUser(dbUser);
        dataSource.setPassword(dbPassword);
        dataSource.setURL(dbUrl);
        
//        try (Connection connection = dataSource.getConnection()) {
//            System.out.println("✅ Successfully connected to Oracle DB: " + connection.getMetaData().getDatabaseProductVersion());
//        }
        return dataSource;
    }
}
