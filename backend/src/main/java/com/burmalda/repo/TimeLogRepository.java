package com.burmalda.repo;

import com.burmalda.domain.TimeLog;
import com.burmalda.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TimeLogRepository extends JpaRepository<TimeLog, Long> {

    /** Интервал [from, to) — to не включительно. */
    List<TimeLog> findByUserAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(
            User user, LocalDateTime from, LocalDateTime to);
}
