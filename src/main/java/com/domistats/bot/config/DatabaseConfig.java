package com.domistats.bot.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DatabaseConfig {

    @Bean
    public javax.sql.DataSource dataSource() {
        String databaseUrl = System.getenv("DATABASE_URL");
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.postgresql.Driver");

        if (databaseUrl != null && !databaseUrl.isBlank()) {
            URI uri = URI.create(databaseUrl.startsWith("postgresql://")
                    ? databaseUrl.replaceFirst("^postgresql://", "postgres://")
                    : databaseUrl);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
            config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + path);
            config.setUsername(uri.getUserInfo() != null ? uri.getUserInfo().split(":", 2)[0] : null);
            config.setPassword(uri.getUserInfo() != null && uri.getUserInfo().contains(":")
                    ? uri.getUserInfo().split(":", 2)[1] : null);
        } else {
            config.setJdbcUrl(System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/domistats_bot"));
            config.setUsername(System.getenv().getOrDefault("DB_USERNAME", "postgres"));
            config.setPassword(System.getenv().getOrDefault("DB_PASSWORD", ""));
        }
        return new HikariDataSource(config);
    }
}
