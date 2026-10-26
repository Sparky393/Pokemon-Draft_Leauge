package com.pokemondraft.leagueportal.services;

import com.pokemondraft.leagueportal.repositories.TransactionRecordRepository;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Fresh revision on each read; complete responses, bounded immutable history chunks. */
@Service
public class TransactionHistoryService {
  public record Entry(Long id, String type, String description, LocalDateTime createdAt) {
  }

  private record Chunk(List<Entry> rows, long loadedAt) {
  }

  private static final int BUCKET_SIZE = 256, MAX_CHUNKS = 32;
  private static final long REFRESH_NANOS = java.time.Duration.ofSeconds(30).toNanos();
  private final TransactionRecordRepository transactions;
  private final Map<Long, Chunk> chunks = new HashMap<>();
  private long cachedCount = -1;
  private Long cachedLatest;
  private List<Long> bucketIds = List.of();

  public TransactionHistoryService(TransactionRecordRepository transactions) {
    this.transactions = transactions;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public synchronized List<Entry> history() {
    var revision = transactions.revision();
    if (cachedCount != revision.getRowCount()
        || !Objects.equals(cachedLatest, revision.getLatestId())) {
      // Keep complete older chunks on pure append. Deletions/resets invalidate all chunks.
      boolean append = cachedLatest != null && revision.getLatestId() != null
          && revision.getLatestId() > cachedLatest && revision.getRowCount() > cachedCount
          && transactions.countByIdLessThanEqual(cachedLatest) == cachedCount;
      if (append)
        chunks.keySet().removeIf(bucket -> bucket >= cachedLatest / BUCKET_SIZE);
      else
        chunks.clear();
      cachedCount = revision.getRowCount();
      cachedLatest = revision.getLatestId();
      bucketIds = transactions.historyBuckets();
    }
    // Only bucket numbers cross the wire here; gaps from deletes/resets are skipped.
    List<Long> buckets = bucketIds;
    Set<Long> retained = new HashSet<>(buckets.subList(0, Math.min(MAX_CHUNKS, buckets.size())));
    chunks.keySet().retainAll(retained);
    long now = System.nanoTime();
    List<Entry> result = new ArrayList<>();
    for (Long bucket : buckets) {
      Chunk chunk = chunks.get(bucket);
      // Preserve the prior 30-second refresh bound for edits made outside the application.
      if (chunk == null || now - chunk.loadedAt() >= REFRESH_NANOS) {
        List<Entry> rows = transactions
            .findByIdGreaterThanEqualAndIdLessThan(bucket * BUCKET_SIZE, (bucket + 1) * BUCKET_SIZE)
            .stream()
            .map(t -> new Entry(t.getId(), t.getType(), t.getDescription(), t.getCreatedAt()))
            .toList();
        chunk = new Chunk(rows, now);
        if (retained.contains(bucket))
          chunks.put(bucket, chunk);
      }
      result.addAll(chunk.rows());
    }
    // Match the original ordering even for imported/backdated transactions.
    result.sort(
        Comparator.comparing(Entry::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
    return List.copyOf(result);
  }
}
