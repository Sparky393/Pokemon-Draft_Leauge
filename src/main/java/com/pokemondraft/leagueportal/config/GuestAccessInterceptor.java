package com.pokemondraft.leagueportal.config;

import com.pokemondraft.leagueportal.services.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Fail closed for guest sessions: new APIs require an explicit public-read review. */
@Component
public class GuestAccessInterceptor implements HandlerInterceptor, WebMvcConfigurer {
  private static final Set<String> PUBLIC_READS = Set.of("/api/auth/me", "/api/dashboard",
      "/api/dashboard/view", "/api/teams", "/api/pokemon", "/api/pokemon/free-agents",
      "/api/rosters", "/api/draft", "/api/draft/status", "/api/draft/idle-status", "/api/standings",
      "/api/schedule", "/api/playoffs", "/api/settings", "/api/flow/calendar", "/api/flow/schedule",
      "/api/weeks/locks", "/api/transactions");
  private static final Set<String> SESSION_ACTIONS = Set.of("/api/auth/login", "/api/auth/logout",
      "/api/auth/guest");
  private final AuthService auth;

  public GuestAccessInterceptor(AuthService auth) {
    this.auth = auth;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(this).addPathPatterns("/api/**");
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
      Object handler) {
    if (!auth.isGuest(request.getSession(false)))
      return true;
    String path = request.getServletPath();
    // MockMvc and some servlet mappings leave servletPath empty.
    if (path.isEmpty())
      path = request.getRequestURI().substring(request.getContextPath().length());
    String method = request.getMethod();
    if (method.equals("POST") && SESSION_ACTIONS.contains(path))
      return true;
    if ((method.equals("GET") || method.equals("HEAD")) && (PUBLIC_READS.contains(path)
        || path.matches("/api/rosters/[0-9]+") || path.matches("/api/teams/[0-9]+/logo")))
      return true;
    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
        "Guest access is read-only; sign in for team or commissioner actions.");
  }
}
