package com.pokemondraft.leagueportal.controllers;

import com.pokemondraft.leagueportal.entity.AppUser;
import com.pokemondraft.leagueportal.entity.Role;
import com.pokemondraft.leagueportal.entity.Team;
import com.pokemondraft.leagueportal.repositories.TeamRepository;
import com.pokemondraft.leagueportal.services.AuthService;
import jakarta.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Coaches may customize only the team assigned to their session. Logos are stored in the existing
 * database, not Render's ephemeral filesystem.
 */
@RestController
@RequestMapping("/api/teams")
public class TeamBrandingController {
  private static final long MAX_IMAGE_BYTES = 1024 * 1024;
  private static final int MAX_DIMENSION = 2048;

  private record LogoKey(Long teamId, Long version, String contentType) {}

  private record LogoBytes(String contentType, byte[] bytes) {}

  // Bounded across all teams/versions, including unusual imported databases.
  private final java.util.LinkedHashMap<LogoKey, LogoBytes> logoCache =
      new java.util.LinkedHashMap<>(16, 0.75f, true);
  private long cachedBytes;

  private synchronized void invalidateLogo(Long id) {
    var entries = logoCache.entrySet().iterator();
    while (entries.hasNext()) {
      var entry = entries.next();
      if (entry.getKey().teamId().equals(id)) {
        cachedBytes -= entry.getValue().bytes().length;
        entries.remove();
      }
    }
  }

  private synchronized LogoBytes cachedLogo(Long id, TeamRepository.LogoMetadata metadata) {
    var key = new LogoKey(id, metadata.getLogoVersion(), metadata.getLogoContentType());
    var cached = logoCache.get(key);
    if (cached != null) return cached;
    var image =
        teams
            .findLogoImage(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
    if (image.getLogoBase64() == null || image.getLogoContentType() == null)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team has no logo");
    var value =
        new LogoBytes(
            image.getLogoContentType(), Base64.getDecoder().decode(image.getLogoBase64()));
    // Use the version belonging to the bytes even if an upload races this request.
    key = new LogoKey(id, image.getLogoVersion(), image.getLogoContentType());
    if (value.bytes().length <= MAX_IMAGE_BYTES) {
      invalidateLogo(id);
      logoCache.put(key, value);
      cachedBytes += value.bytes().length;
      while (logoCache.size() > 32 || cachedBytes > 16L * MAX_IMAGE_BYTES) {
        var oldest = logoCache.entrySet().iterator();
        cachedBytes -= oldest.next().getValue().bytes().length;
        oldest.remove();
      }
    }
    return value;
  }

  private final AuthService auth;
  private final TeamRepository teams;

  public TeamBrandingController(AuthService auth, TeamRepository teams) {
    this.auth = auth;
    this.teams = teams;
  }

  public record TeamNameRequest(String name) {}

  private Team myTeam(HttpSession session) {
    AppUser user = auth.current(session);
    if (user.getRole() != Role.COACH || user.getTeamId() == null)
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only coaches can customize their team");
    return teams
        .findMetadataById(user.getTeamId())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Your team was not found"));
  }

  @PutMapping("/me/name")
  public Team rename(@RequestBody TeamNameRequest request, HttpSession session) {
    Team team = myTeam(session);
    String name = request == null || request.name() == null ? "" : request.name().trim();
    if (name.length() < 2 || name.length() > 40 || name.chars().anyMatch(Character::isISOControl))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Team name must be 2-40 characters");
    if (teams.existsByNameIgnoreCaseAndIdNot(name, team.getId()))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Another team already uses that name");
    team.setName(name);
    teams.rename(team.getId(), name);
    return team;
  }

  @PostMapping(value = "/me/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Team upload(@RequestParam("file") MultipartFile file, HttpSession session)
      throws IOException {
    Team team = myTeam(session);
    if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Choose an image smaller than 1 MB");
    byte[] content = file.getBytes();
    if (content.length == 0 || content.length > MAX_IMAGE_BYTES)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Choose an image smaller than 1 MB");

    String type = imageType(content);
    if (type == null)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Only PNG, JPG and GIF images are supported");

    try (ImageInputStream input =
        ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
      if (input == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image");
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext())
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image");
      ImageReader reader = readers.next();
      try {
        reader.setInput(input);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION)
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "Logo dimensions must be at most 2048 x 2048");
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException error) {
      if (error instanceof ResponseStatusException response) throw response;
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or damaged image");
    }

    team.setLogoBase64(Base64.getEncoder().encodeToString(content));
    team.setLogoContentType(type);
    team.setLogoVersion(System.currentTimeMillis());
    teams.updateLogo(
        team.getId(), team.getLogoBase64(), team.getLogoContentType(), team.getLogoVersion());
    invalidateLogo(team.getId());
    return team;
  }

  @DeleteMapping("/me/logo")
  public Team removeLogo(HttpSession session) {
    Team team = myTeam(session);
    team.setLogoBase64(null);
    team.setLogoContentType(null);
    team.setLogoVersion(System.currentTimeMillis());
    teams.updateLogo(team.getId(), null, null, team.getLogoVersion());
    invalidateLogo(team.getId());
    return team;
  }

  @GetMapping("/{id}/logo")
  public ResponseEntity<byte[]> logo(@PathVariable Long id, HttpSession session) {
    auth.current(session);
    var metadata =
        teams
            .findLogoMetadata(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
    if (!metadata.getPresent() || metadata.getLogoContentType() == null)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team has no logo");
    var image = cachedLogo(id, metadata);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePrivate())
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.bytes());
  }

  private static String imageType(byte[] b) {
    if (b.length >= 8
        && (b[0] & 255) == 137
        && b[1] == 80
        && b[2] == 78
        && b[3] == 71
        && b[4] == 13
        && b[5] == 10
        && b[6] == 26
        && b[7] == 10) return MediaType.IMAGE_PNG_VALUE;
    if (b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255)
      return MediaType.IMAGE_JPEG_VALUE;
    if (b.length >= 6
        && b[0] == 'G'
        && b[1] == 'I'
        && b[2] == 'F'
        && b[3] == '8'
        && (b[4] == '7' || b[4] == '9')
        && b[5] == 'a') return MediaType.IMAGE_GIF_VALUE;
    return null;
  }
}
