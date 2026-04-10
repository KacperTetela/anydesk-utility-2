package raven.history;

import java.time.LocalDateTime;

public record ConnectionRecord(String anydeskId, LocalDateTime connectionDate, long durationSeconds) {
}

