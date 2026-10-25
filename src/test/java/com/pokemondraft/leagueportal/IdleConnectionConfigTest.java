package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class IdleConnectionConfigTest {
  @Test
  void actualApplicationPropertiesDisableKeepalivesAndAllowThePoolToDrain() throws Exception {
    Properties properties = new Properties();
    try (var input = getClass().getResourceAsStream("/application.properties")) {
      assertNotNull(input);
      properties.load(input);
    }
    Map<String, Object> settings = new HashMap<>();
    properties.forEach((key, value) -> {
      if (key.toString().startsWith("spring.datasource.hikari.")) settings.put(key.toString(), value);
    });
    HikariConfig config = new Binder(new MapConfigurationPropertySource(settings))
        .bind("spring.datasource.hikari", Bindable.of(HikariConfig.class)).get();
    config.setJdbcUrl("jdbc:postgresql://127.0.0.1:1/unused");
    config.validate(); // Configuration only: never construct a datasource or open a connection.
    assertEquals(0, config.getMinimumIdle());
    assertEquals(0, config.getKeepaliveTime());
    assertEquals(60000, config.getIdleTimeout());
    assertEquals(10, config.getMaximumPoolSize(), "Capacity remains available on demand");
  }
}
