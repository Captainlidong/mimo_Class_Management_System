package com.classmgmt.service;

import com.classmgmt.domain.OperationLog;
import com.classmgmt.repo.OperationLogRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作日志审计：关键动作（名单替换/锁定、备份恢复、换届、删除类操作、转收集等）留痕。
 * 记录失败不影响主流程；保留 180 天。
 */
@Service
public class OperationLogService {

    private static final long KEEP_DAYS = 180;
    private static final int LIST_LIMIT = 200;

    private final OperationLogRepository repository;

    public OperationLogService(OperationLogRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void cleanupExpired() {
        repository.deleteByCreatedAtBefore(Instant.now().minus(Duration.ofDays(KEEP_DAYS)));
    }

    /** 记录一条审计日志；任何异常吞掉，绝不影响主流程。 */
    @Transactional
    public void log(String action, String module, String summary) {
        try {
            OperationLog entry = new OperationLog();
            entry.setAction(action);
            entry.setModule(module);
            entry.setSummary(summary == null ? "" : summary.length() > 255 ? summary.substring(0, 255) : summary);
            entry.setCreatedAt(Instant.now());
            repository.save(entry);
        } catch (Exception ignored) {
            // 审计失败不阻塞业务
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list() {
        List<OperationLog> all = repository.findAllByOrderByCreatedAtDescIdDesc();
        List<Map<String, Object>> items = new ArrayList<>();
        int limit = Math.min(all.size(), LIST_LIMIT);
        for (int i = 0; i < limit; i++) {
            OperationLog entry = all.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", entry.getId());
            m.put("action", entry.getAction());
            m.put("module", entry.getModule());
            m.put("summary", entry.getSummary());
            m.put("createdAt", entry.getCreatedAt());
            items.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", all.size());
        return result;
    }
}
