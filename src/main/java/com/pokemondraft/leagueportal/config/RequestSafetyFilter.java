package com.pokemondraft.leagueportal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Block cross-origin browser requests from using authenticated session cookies. */
@Component
public class RequestSafetyFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

    String method = request.getMethod();
    boolean modifying = !(method.equals("GET") || method.equals("HEAD")
        || method.equals("OPTIONS"));
    if (modifying && request.getRequestURI().startsWith("/api/")) {
      // Browser requests from our own UI carry this non-simple header.
      // Combined with same-origin checks, it blocks ordinary cross-site forms
      // from submitting authenticated commissioner actions.
      if (!"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
        response.sendError(403, "Use the league website to submit this action");
        return;
      }
      // Render's HTTPS edge provides X-Forwarded-Host/Proto; only trust the
      // host/protocol normalized by Spring when it is configured to do so.
      String origin = request.getHeader("Origin");
      if (origin == null || origin.isBlank()) {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank())
          origin = referer;
      }
      if (origin != null && !origin.isBlank()) {
        try {
          URI uri = URI.create(origin);
          String requestHost = request.getServerName();
          if (!uri.getHost().equalsIgnoreCase(requestHost)) {
            response.sendError(403, "Cross-site requests are not allowed");
            return;
          }
        } catch (IllegalArgumentException | NullPointerException ex) {
          response.sendError(403, "Invalid request origin");
          return;
        }
      }
    }
    chain.doFilter(request, response);
  }
}
