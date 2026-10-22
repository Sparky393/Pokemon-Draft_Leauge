package com.pokemondraft.leagueportal.services;

import com.pokemondraft.leagueportal.repositories.TransactionRecordRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Journal rows are append-only in the application (or removed by league reset). Check a tiny
 * database revision on EVERY read so writes on any Render instance invalidate the cached history.
 * No TTL cache is used for active league state.
 */
@Service
public class TransactionHistoryService {
  public record Entry(Long id, String type, String description, LocalDateTime createdAt) {}

  private final TransactionRecordRepository transactions;
  private List<Entry> cached = List.of();
  private long cachedCount = -1;
  private Long cachedLatest;
  private long refreshedAt;

  public TransactionHistoryService(TransactionRecordRepository transactions) {
    this.transactions = transactions;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public synchronized List<Entry> history() {
    var revision = transactions.revision();
    long now = System.nanoTime();
    // Bound memory and also refresh periodically for edits made outside the app.
    if (revision.getRowCount() <= 5000
        && cachedCount == revision.getRowCount()
        && java.util.Objects.equals(cachedLatest, revision.getLatestId())
        && now - refreshedAt < java.time.Duration.ofSeconds(30).toNanos()) return cached;
    List<Entry> rows =
        transactions.findAllByOrderByCreatedAtDesc().stream()
            .map(t -> new Entry(t.getId(), t.getType(), t.getDescription(), t.getCreatedAt()))
            .toList();
    if (rows.size() <= 5000) {
      cached = rows;
      cachedCount = revision.getRowCount();
      cachedLatest = revision.getLatestId();
      refreshedAt = now;
    } else {
      cached = List.of();
      cachedCount = -1;
    }
    return rows;
  }
}
