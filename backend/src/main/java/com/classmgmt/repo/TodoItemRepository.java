package com.classmgmt.repo;

import com.classmgmt.domain.TodoItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface TodoItemRepository extends JpaRepository<TodoItem, Long> {

    long countByDoneFalse();

    long countByDoneFalseAndDueAtBefore(Instant now);

    /** 今日到期：dueAt 落在今天 [0点, 次日0点) 区间的未完成待办。 */
    long countByDoneFalseAndDueAtGreaterThanEqualAndDueAtLessThan(Instant start, Instant end);
}

