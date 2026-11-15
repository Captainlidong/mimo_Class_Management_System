package com.classmgmt.service;

import com.classmgmt.domain.Announcement;
import com.classmgmt.domain.CheckRecord;
import com.classmgmt.domain.CollectionTask;
import com.classmgmt.domain.GroupInfo;
import com.classmgmt.domain.LeaveRecord;
import com.classmgmt.domain.LongTask;
import com.classmgmt.domain.ScholarshipBatch;
import com.classmgmt.domain.Student;
import com.classmgmt.domain.SystemSetting;
import com.classmgmt.domain.Term;
import com.classmgmt.domain.TodoItem;
import com.classmgmt.repo.SystemSettingRepository;
import com.classmgmt.repo.TermRepository;
import com.classmgmt.web.GlobalExceptionHandler;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学期档案：换届归档维度。
 * 双指针语义——
 *  - active（唯一未归档学期）：所有新增数据归属它，历史学期写入一律拒绝；
 *  - view（SystemSetting current_term_id）：浏览过滤上下文，切到历史学期即为只读浏览。
 */
@Service
public class TermService {

    public static final String CURRENT_TERM_KEY = "current_term_id";

    private final TermRepository termRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final EntityManager entityManager;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public TermService(TermRepository termRepository,
                       SystemSettingRepository systemSettingRepository,
                       EntityManager entityManager,
                       org.springframework.transaction.support.TransactionTemplate transactionTemplate) {
        this.termRepository = termRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
    }

    /** 兼容迁移：term 表为空时创建「默认学期」，并把既有业务数据一次性挂到它名下。 */
    @PostConstruct
    public void ensureDefault() {
        if (termRepository.count() > 0) {
            return;
        }
        // @PostConstruct 阶段 @Transactional 不生效，用事务模板显式包住批量迁移
        transactionTemplate.executeWithoutResult(status -> migrate());
    }

    private void migrate() {
        if (termRepository.count() > 0) {
            return;
        }
        Term term = new Term();
        term.setName("默认学期");
        term.setArchived(false);
        term.setCreatedAt(Instant.now());
        termRepository.save(term);
        setViewPointer(term.getId());
        Class<?>[] scoped = {Student.class, LongTask.class, CheckRecord.class, CollectionTask.class,
                LeaveRecord.class, TodoItem.class, ScholarshipBatch.class, Announcement.class, GroupInfo.class};
        for (Class<?> e : scoped) {
            entityManager.createQuery("UPDATE " + e.getSimpleName() + " e SET e.termId = :tid WHERE e.termId IS NULL")
                    .setParameter("tid", term.getId())
                    .executeUpdate();
        }
    }

    /** 浏览学期指针（SystemSetting）。 */
    @Transactional
    public Long getViewTermId() {
        return systemSettingRepository.findBySettingKey(CURRENT_TERM_KEY)
                .map(s -> {
                    try {
                        return Long.parseLong(s.getSettingValue());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(getActiveTermId());
    }

    /** 唯一未归档学期（所有新增数据的归属）。 */
    @Transactional(readOnly = true)
    public Term getActiveTerm() {
        return termRepository.findAll().stream()
                .filter(t -> !Boolean.TRUE.equals(t.getArchived()))
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Long getActiveTermId() {
        Term t = getActiveTerm();
        return t == null ? null : t.getId();
    }

    @Transactional(readOnly = true)
    public boolean isViewingActive() {
        Long view = getViewTermId();
        Long active = getActiveTermId();
        return view == null || view.equals(active);
    }

    /** 浏览学期匹配（列表过滤用）：null（迁移前遗留）视为 active。 */
    @Transactional(readOnly = true)
    public boolean matchesView(Long termId) {
        Long view = getViewTermId();
        Long active = getActiveTermId();
        Long effective = termId == null ? active : termId;
        return effective != null && effective.equals(view);
    }

    /** 浏览历史学期时禁止新增（新增数据归属 active 学期，会造成"看不到自己刚建的数据"）。 */
    @Transactional
    public void ensureActiveView() {
        if (!isViewingActive()) {
            throw GlobalExceptionHandler.badRequest("当前处于历史学期浏览模式（只读），请先切换回当前学期再新增");
        }
    }

    /** 历史学期实体只读：更新/删除前校验实体归属。 */
    @Transactional
    public void ensureWritable(Long termId) {
        Long active = getActiveTermId();
        boolean ok = termId == null || termId.equals(active);
        if (!ok) {
            throw GlobalExceptionHandler.badRequest("历史学期数据为只读，无法修改或删除");
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list() {
        Long viewId = getViewTermId();
        Long activeId = getActiveTermId();
        List<Map<String, Object>> terms = new ArrayList<>();
        for (Term t : termRepository.findAllByOrderByCreatedAtDescIdDesc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getName());
            m.put("startDate", t.getStartDate());
            m.put("endDate", t.getEndDate());
            m.put("archived", Boolean.TRUE.equals(t.getArchived()));
            m.put("createdAt", t.getCreatedAt());
            m.put("current", t.getId().equals(viewId));
            m.put("active", t.getId().equals(activeId));
            terms.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("terms", terms);
        result.put("viewTermId", viewId);
        result.put("activeTermId", activeId);
        return result;
    }

    /** 切换浏览学期（历史学期只读浏览）。 */
    @Transactional
    public void switchTo(Long id) {
        Term term = termRepository.findById(id)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("学期不存在"));
        setViewPointer(term.getId());
    }

    /** 创建新学期并换届：旧学期全部归档（只读），新学期从空白名单开始。 */
    @Transactional
    public Map<String, Object> createAndSwitch(Map<String, Object> body) {
        String name = body.get("name") == null ? "" : String.valueOf(body.get("name")).trim();
        if (name.isEmpty()) {
            throw GlobalExceptionHandler.badRequest("学期名称不能为空");
        }
        if (name.length() > 64) {
            throw GlobalExceptionHandler.badRequest("学期名称过长（最多 64 字）");
        }
        for (Term t : termRepository.findAll()) {
            t.setArchived(true);
        }
        Term term = new Term();
        term.setName(name);
        term.setStartDate(parseDate(body.get("startDate")));
        term.setEndDate(parseDate(body.get("endDate")));
        term.setArchived(false);
        term.setCreatedAt(Instant.now());
        termRepository.save(term);
        setViewPointer(term.getId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", term.getId());
        m.put("name", term.getName());
        return m;
    }

    private void setViewPointer(Long termId) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(CURRENT_TERM_KEY)
                .orElseGet(() -> {
                    SystemSetting s = new SystemSetting();
                    s.setSettingKey(CURRENT_TERM_KEY);
                    return s;
                });
        setting.setSettingValue(String.valueOf(termId));
        systemSettingRepository.save(setting);
    }

    private LocalDate parseDate(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(raw).trim());
        } catch (Exception e) {
            throw GlobalExceptionHandler.badRequest("日期格式不正确（应为 yyyy-MM-dd）：" + raw);
        }
    }
}
