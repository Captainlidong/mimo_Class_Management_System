package com.classmgmt.service;

import com.classmgmt.config.AppProperties;
import com.classmgmt.domain.Student;
import com.classmgmt.domain.SystemSetting;
import com.classmgmt.dto.StudentListDTO;
import com.classmgmt.dto.StudentsReplaceRequest;
import com.classmgmt.repo.StudentRepository;
import com.classmgmt.repo.SystemSettingRepository;
import com.classmgmt.web.GlobalExceptionHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StudentService {

    public static final String LOCK_KEY = "students_locked";

    public static final String[] SAMPLE_NAMES = {
            "王伟", "李娜", "张敏", "刘洋", "陈静", "杨帆", "赵磊", "黄婷", "周杰",
            "吴倩", "徐强", "孙丽", "胡军", "朱霞", "高峰", "林芳", "何斌", "郭婷",
            "马超", "罗娟", "梁宇", "宋佳", "郑凯", "谢婷", "唐明", "韩雪", "曹阳",
            "邓丽", "冯刚", "彭辉", "曾芳", "肖鹏", "田甜", "董磊", "袁媛", "潘伟"
    };

    private final StudentRepository studentRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final com.classmgmt.repo.GroupMemberRepository groupMemberRepository;
    private final com.classmgmt.repo.GroupRepository groupRepository;
    private final AppProperties appProperties;
    private final TermService termService;
    private final OperationLogService operationLogService;

    public StudentService(StudentRepository studentRepository,
                          SystemSettingRepository systemSettingRepository,
                          com.classmgmt.repo.GroupMemberRepository groupMemberRepository,
                          com.classmgmt.repo.GroupRepository groupRepository,
                          AppProperties appProperties,
                          TermService termService,
                          OperationLogService operationLogService) {
        this.studentRepository = studentRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupRepository = groupRepository;
        this.appProperties = appProperties;
        this.termService = termService;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public void seedIfEmpty() {
        if (!appProperties.isSeedSample()) {
            return;
        }
        if (studentRepository.count() > 0) {
            return;
        }
        termService.ensureDefault();
        Long termId = termService.getActiveTermId();
        List<Student> list = new ArrayList<>();
        for (int i = 0; i < SAMPLE_NAMES.length; i++) {
            Student s = new Student();
            s.setName(SAMPLE_NAMES[i]);
            s.setStudentNo(String.format("2024%02d", i + 1));
            s.setSortOrder(i + 1);
            s.setTermId(termId);
            list.add(s);
        }
        studentRepository.saveAll(list);
        setLocked(false);
    }

    public boolean isLocked() {
        return systemSettingRepository.findBySettingKey(LOCK_KEY)
                .map(s -> "true".equalsIgnoreCase(s.getSettingValue()))
                .orElse(false);
    }

    @Transactional
    public void setLocked(boolean locked) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(LOCK_KEY)
                .orElseGet(SystemSetting::new);
        setting.setSettingKey(LOCK_KEY);
        setting.setSettingValue(String.valueOf(locked));
        systemSettingRepository.save(setting);
    }

    @Transactional(readOnly = true)
    public StudentListDTO list() {
        // 名单管理口径：浏览学期全部学生（含离班，前端用状态徽标区分）
        Long viewTermId = termService.getViewTermId();
        List<Student> students = new ArrayList<>();
        for (Student s : studentRepository.findAllByOrderBySortOrderAscIdAsc()) {
            Long effective = s.getTermId() == null ? termService.getActiveTermId() : s.getTermId();
            if (effective != null && effective.equals(viewTermId)) {
                students.add(s);
            }
        }
        StudentListDTO dto = new StudentListDTO();
        dto.setLocked(isLocked());
        dto.setCount(students.size());
        List<StudentListDTO.StudentDTO> items = new ArrayList<>();
        for (Student s : students) {
            items.add(toDto(s));
        }
        dto.setStudents(items);
        return dto;
    }

    /** 在班口径（导出名单/收集默认全班/移动端展示）：浏览学期内 status 非 left 的学生。 */
    @Transactional(readOnly = true)
    public StudentListDTO listActive() {
        Long viewTermId = termService.getViewTermId();
        List<Student> students = new ArrayList<>();
        for (Student s : studentRepository.findAllByOrderBySortOrderAscIdAsc()) {
            Long effective = s.getTermId() == null ? termService.getActiveTermId() : s.getTermId();
            if (effective != null && effective.equals(viewTermId) && s.isInSchool()) {
                students.add(s);
            }
        }
        StudentListDTO dto = new StudentListDTO();
        dto.setLocked(isLocked());
        dto.setCount(students.size());
        List<StudentListDTO.StudentDTO> items = new ArrayList<>();
        for (Student s : students) {
            items.add(toDto(s));
        }
        dto.setStudents(items);
        return dto;
    }

    @Transactional
    public StudentListDTO replace(StudentsReplaceRequest request) {
        if (isLocked()) {
            throw GlobalExceptionHandler.conflict("基准名单已锁定，请先解锁后再修改");
        }
        termService.ensureActiveView();
        Long activeTermId = termService.getActiveTermId();
        if (request.getStudents() == null || request.getStudents().isEmpty()) {
            throw GlobalExceptionHandler.badRequest("学生名单不能为空");
        }

        // 1. 解析新名单（去重：按学号或姓名，避免重复行造成重复学生）
        List<Student> incoming = new ArrayList<>();
        Set<String> incomingSeen = new LinkedHashSet<>();
        int order = 1;
        for (StudentsReplaceRequest.Item item : request.getStudents()) {
            String name = NameNormalizer.normalize(item.getName());
            if (NameNormalizer.isBlankOrSymbol(name)) {
                throw GlobalExceptionHandler.badRequest("存在无效姓名，请检查名单");
            }
            String no = item.getStudentNo() == null ? "" : NameNormalizer.normalize(item.getStudentNo());
            String dedupeKey = no.isEmpty() ? "name:" + name : "no:" + no;
            if (!incomingSeen.add(dedupeKey)) {
                continue;
            }
            Student s = new Student();
            s.setName(name);
            s.setStudentNo(item.getStudentNo());
            s.setSortOrder(item.getSortOrder() == null ? order : item.getSortOrder());
            s.setTermId(activeTermId);
            s.setStatus(Student.STATUS_IN);
            incoming.add(s);
            order++;
        }

        // 2. 与当前学期现有学生（含离班）做匹配：学号精确优先，姓名兜底
        List<Student> existing = studentRepository.findByTermId(activeTermId);
        Map<String, Student> existingByNo = new LinkedHashMap<>();
        Map<String, Student> existingByName = new LinkedHashMap<>();
        for (Student e : existing) {
            if (e.getStudentNo() != null && !e.getStudentNo().isBlank()) {
                existingByNo.putIfAbsent(NameNormalizer.normalize(e.getStudentNo()), e);
            }
            existingByName.putIfAbsent(NameNormalizer.normalize(e.getName()), e);
        }

        Set<Long> matchedIds = new LinkedHashSet<>();
        List<Student> toSave = new ArrayList<>();
        for (Student in : incoming) {
            Student match = in.getStudentNo() != null && !in.getStudentNo().isBlank()
                    ? existingByNo.get(NameNormalizer.normalize(in.getStudentNo()))
                    : null;
            if (match == null) {
                match = existingByName.get(NameNormalizer.normalize(in.getName()));
            }
            if (match != null && matchedIds.add(match.getId())) {
                // 更新既有学生：保留 id，收集条目/请假/分组成员等历史引用全部稳定
                match.setName(in.getName());
                match.setStudentNo(in.getStudentNo());
                match.setSortOrder(in.getSortOrder());
                match.setStatus(Student.STATUS_IN.equals(in.getStatus()) ? Student.STATUS_IN : Student.STATUS_LEFT);
                toSave.add(match);
            } else if (match == null) {
                if (Student.STATUS_LEFT.equals(in.getStatus())) {
                    // 新行直接带离班状态（页面粘贴场景默认在班，理论上少见）
                    in.setStatus(Student.STATUS_LEFT);
                }
                toSave.add(in);
            }
        }

        // 3. 现存但在新名单中被移除的在班学生 → 软移出（物理保留，状态 left）
        int removed = 0;
        for (Student e : existing) {
            if (!matchedIds.contains(e.getId()) && e.isInSchool()) {
                e.setStatus(Student.STATUS_LEFT);
                toSave.add(e);
                removed++;
            }
        }

        studentRepository.saveAll(toSave);
        operationLogService.log("roster.replace", "名单",
                "同步名单：在班 " + incoming.size() + " 人" + (removed > 0 ? "，移出 " + removed + " 人" : ""));
        if (request.getLocked() != null) {
            setLocked(request.getLocked());
        }
        return list();
    }

    @Transactional(readOnly = true)
    public List<Student> baseline() {
        // 筛查/收集默认全班口径 = 浏览学期内的在班学生；离班学生不算未参与
        Long viewTermId = termService.getViewTermId();
        List<Student> students = new ArrayList<>();
        for (Student s : studentRepository.findAllByOrderBySortOrderAscIdAsc()) {
            Long effective = s.getTermId() == null ? termService.getActiveTermId() : s.getTermId();
            if (effective != null && effective.equals(viewTermId) && s.isInSchool()) {
                students.add(s);
            }
        }
        return students;
    }

    /** 标记离班（软移出：物理保留，历史收集条目/请假/分组成员引用不受影响）。 */
    @Transactional
    public StudentListDTO.StudentDTO markLeave(Long id) {
        Student s = studentRepository.findById(id)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("学生不存在"));
        termService.ensureWritable(s.getTermId());
        s.setStatus(Student.STATUS_LEFT);
        studentRepository.save(s);
        operationLogService.log("roster.markLeave", "名单", "标记离班：" + s.getName());
        return toDto(s);
    }

    /** 恢复在班。 */
    @Transactional
    public StudentListDTO.StudentDTO restore(Long id) {
        Student s = studentRepository.findById(id)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("学生不存在"));
        termService.ensureWritable(s.getTermId());
        s.setStatus(Student.STATUS_IN);
        studentRepository.save(s);
        operationLogService.log("roster.restore", "名单", "恢复在班：" + s.getName());
        return toDto(s);
    }

    private StudentListDTO.StudentDTO toDto(Student s) {
        StudentListDTO.StudentDTO item = new StudentListDTO.StudentDTO();
        item.setId(s.getId());
        item.setName(s.getName());
        item.setStudentNo(s.getStudentNo());
        item.setSortOrder(s.getSortOrder());
        item.setStatus(s.getStatus());
        return item;
    }
}
