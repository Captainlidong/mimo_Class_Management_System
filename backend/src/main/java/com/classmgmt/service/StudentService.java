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
import java.util.List;

@Service
public class StudentService {

    public static final String LOCK_KEY = "students_locked";

    private static final String[] SAMPLE_NAMES = {
            "王伟", "李娜", "张敏", "刘洋", "陈静", "杨帆", "赵磊", "黄婷", "周杰",
            "吴倩", "徐强", "孙丽", "胡军", "朱霞", "高峰", "林芳", "何斌", "郭婷",
            "马超", "罗娟", "梁宇", "宋佳", "郑凯", "谢婷", "唐明", "韩雪", "曹阳",
            "邓丽", "冯刚", "彭辉", "曾芳", "肖鹏", "田甜", "董磊", "袁媛", "潘伟"
    };

    private final StudentRepository studentRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final com.classmgmt.repo.GroupMemberRepository groupMemberRepository;
    private final AppProperties appProperties;

    public StudentService(StudentRepository studentRepository,
                          SystemSettingRepository systemSettingRepository,
                          com.classmgmt.repo.GroupMemberRepository groupMemberRepository,
                          AppProperties appProperties) {
        this.studentRepository = studentRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.appProperties = appProperties;
    }

    @Transactional
    public void seedIfEmpty() {
        if (!appProperties.isSeedSample()) {
            return;
        }
        if (studentRepository.count() > 0) {
            return;
        }
        List<Student> list = new ArrayList<>();
        for (int i = 0; i < SAMPLE_NAMES.length; i++) {
            Student s = new Student();
            s.setName(SAMPLE_NAMES[i]);
            s.setStudentNo(String.format("2024%02d", i + 1));
            s.setSortOrder(i + 1);
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
        List<Student> students = studentRepository.findAllByOrderBySortOrderAscIdAsc();
        StudentListDTO dto = new StudentListDTO();
        dto.setLocked(isLocked());
        dto.setCount(students.size());
        List<StudentListDTO.StudentDTO> items = new ArrayList<>();
        for (Student s : students) {
            StudentListDTO.StudentDTO item = new StudentListDTO.StudentDTO();
            item.setId(s.getId());
            item.setName(s.getName());
            item.setStudentNo(s.getStudentNo());
            item.setSortOrder(s.getSortOrder());
            items.add(item);
        }
        dto.setStudents(items);
        return dto;
    }

    @Transactional
    public StudentListDTO replace(StudentsReplaceRequest request) {
        if (isLocked()) {
            throw GlobalExceptionHandler.conflict("基准名单已锁定，请先解锁后再修改");
        }
        if (request.getStudents() == null || request.getStudents().isEmpty()) {
            throw GlobalExceptionHandler.badRequest("学生名单不能为空");
        }
        List<Student> next = new ArrayList<>();
        int order = 1;
        for (StudentsReplaceRequest.Item item : request.getStudents()) {
            String name = NameNormalizer.normalize(item.getName());
            if (NameNormalizer.isBlankOrSymbol(name)) {
                throw GlobalExceptionHandler.badRequest("存在无效姓名，请检查名单");
            }
            Student s = new Student();
            s.setId(null);
            s.setName(name);
            s.setStudentNo(item.getStudentNo());
            s.setSortOrder(item.getSortOrder() == null ? order : item.getSortOrder());
            next.add(s);
            order++;
        }
        studentRepository.deleteAll();
        studentRepository.flush();
        // 基准名单整表替换后，原分组成员关系全部失效，避免孤儿数据
        groupMemberRepository.deleteAll();
        groupMemberRepository.flush();
        studentRepository.saveAll(next);
        if (request.getLocked() != null) {
            setLocked(request.getLocked());
        }
        return list();
    }

    @Transactional(readOnly = true)
    public List<Student> baseline() {
        return studentRepository.findAllByOrderBySortOrderAscIdAsc();
    }
}
