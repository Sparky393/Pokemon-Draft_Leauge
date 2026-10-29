package com.pokemondraft.leagueportal.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Production deployments must never boot against the local H2 fallback. Does not query or modify
 * Neon; the app fails to start if its URL is missing.
 */
@Component
public class ProductionDatabaseSafety {
  @PostConstruct
  public void validate() {
    boolean onRender =
        System.getenv("RENDER") != null && !"false".equalsIgnoreCase(System.getenv("RENDER"));
    boolean explicitlyProduction = "prod".equals(System.getenv("SPRING_PROFILES_ACTIVE"));
    if (!onRender && !explicitlyProduction) return;

    String url = System.getenv("DATABASE_URL");
    if (url == null
        || url.isBlank()
        || (!url.startsWith("jdbc:postgresql:") && !url.startsWith("postgresql:"))) {
      throw new IllegalStateException(
          "Production requires DATABASE_URL pointing to Neon PostgreSQL. "
              + "Refusing to start with the local H2 fallback.");
    }
  }
}
