package com.classmgmt.web;

import com.classmgmt.domain.Announcement;
import com.classmgmt.domain.AnnouncementFile;
import com.classmgmt.domain.AnnouncementVersion;
import com.classmgmt.domain.CheckRecord;
import com.classmgmt.domain.GroupInfo;
import com.classmgmt.domain.GroupMember;
import com.classmgmt.domain.LeaveRecord;
import com.classmgmt.domain.LongTask;
import com.classmgmt.domain.ScholarshipAward;
import com.classmgmt.domain.ScholarshipBatch;
import com.classmgmt.domain.Student;
import com.classmgmt.repo.*;
import com.classmgmt.service.StudentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SystemController {

    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final LongTaskRepository longTaskRepository;
    private final CheckRecordRepository checkRecordRepository;
    private final AnnouncementRepository announcementRepository;
    private final AnnouncementVersionRepository announcementVersionRepository;
    private final AnnouncementFileRepository announcementFileRepository;
    private final LeaveRecordRepository leaveRecordRepository;
    private final com.classmgmt.repo.ScholarshipBatchRepository scholarshipBatchRepository;
    private final com.classmgmt.repo.ScholarshipAwardRepository scholarshipAwardRepository;
    private final StudentService studentService;
    private final ObjectMapper objectMapper;

    private final com.classmgmt.config.AppProperties appProperties;

    public SystemController(StudentRepository studentRepository,
                            GroupRepository groupRepository,
                            GroupMemberRepository groupMemberRepository,
                            LongTaskRepository longTaskRepository,
                            CheckRecordRepository checkRecordRepository,
                            AnnouncementRepository announcementRepository,
                            AnnouncementVersionRepository announcementVersionRepository,
                            AnnouncementFileRepository announcementFileRepository,
                            LeaveRecordRepository leaveRecordRepository,
                            com.classmgmt.repo.ScholarshipBatchRepository scholarshipBatchRepository,
                            com.classmgmt.repo.ScholarshipAwardRepository scholarshipAwardRepository,
                            StudentService studentService,
                            ObjectMapper objectMapper,
                            com.classmgmt.config.AppProperties appProperties) {
        this.studentRepository = studentRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.longTaskRepository = longTaskRepository;
        this.checkRecordRepository = checkRecordRepository;
        this.announcementRepository = announcementRepository;
        this.announcementVersionRepository = announcementVersionRepository;
        this.announcementFileRepository = announcementFileRepository;
        this.leaveRecordRepository = leaveRecordRepository;
        this.scholarshipBatchRepository = scholarshipBatchRepository;
        this.scholarshipAwardRepository = scholarshipAwardRepository;
        this.studentService = studentService;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        String pw = appProperties.getAccessPassword();
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "time", Instant.now().toString(),
                "students", studentRepository.count(),
                "passwordRequired", pw != null && !pw.isBlank()
        ));
    }

    @GetMapping("/backup/export")
    public ResponseEntity<Map<String, Object>> exportBackup() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportedAt", Instant.now().toString());
        data.put("locked", studentService.isLocked());
        data.put("students", studentRepository.findAllByOrderBySortOrderAscIdAsc());
        data.put("groups", groupRepository.findAll());
        data.put("groupMembers", groupMemberRepository.findAll());
        data.put("tasks", longTaskRepository.findAll());
        data.put("checkRecords", checkRecordRepository.findAll());
        data.put("announcements", announcementRepository.findAll());
        data.put("announcementVersions", announcementVersionRepository.findAll());
        data.put("announcementFiles", announcementFileRepository.findAll());
        data.put("leaveRecords", leaveRecordRepository.findAll());
        data.put("scholarshipBatches", scholarshipBatchRepository.findAll());
        data.put("scholarshipAwards", scholarshipAwardRepository.findAll());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(data);
    }

    @PostMapping(value = "/backup/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> importBackup(@RequestBody Map<String, Object> body) {
        try {
            Map<Long, Long> studentIdMap = new HashMap<>();
            Map<Long, Long> groupIdMap = new HashMap<>();
            Map<Long, Long> taskIdMap = new HashMap<>();
            Map<Long, Long> announcementIdMap = new HashMap<>();
            Map<Long, Long> scholarshipBatchIdMap = new HashMap<>();

            if (body.get("students") instanceof List<?> students) {
                studentRepository.deleteAll();
                studentRepository.flush();
                groupMemberRepository.deleteAll();
                groupMemberRepository.flush();
                for (Object o : students) {
                    Student s = objectMapper.convertValue(o, Student.class);
                    Long oldId = s.getId();
                    s.setId(null);
                    Student saved = studentRepository.save(s);
                    if (oldId != null) {
                        studentIdMap.put(oldId, saved.getId());
                    }
                }
            }

            if (body.get("groups") instanceof List<?> groups) {
                groupRepository.deleteAll();
                groupRepository.flush();
                groupMemberRepository.deleteAll();
                groupMemberRepository.flush();
                for (Object o : groups) {
                    GroupInfo g = objectMapper.convertValue(o, GroupInfo.class);
                    Long oldId = g.getId();
                    g.setId(null);
                    GroupInfo saved = groupRepository.save(g);
                    if (oldId != null) {
                        groupIdMap.put(oldId, saved.getId());
                    }
                }
            }

            if (body.get("groupMembers") instanceof List<?> members) {
                groupMemberRepository.deleteAll();
                groupMemberRepository.flush();
                for (Object o : members) {
                    GroupMember gm = objectMapper.convertValue(o, GroupMember.class);
                    Long oldGroupId = gm.getGroupId();
                    Long oldStudentId = gm.getStudentId();
                    Long newGroupId = groupIdMap.getOrDefault(oldGroupId, oldGroupId);
                    Long newStudentId = studentIdMap.getOrDefault(oldStudentId, oldStudentId);
                    if (!groupRepository.existsById(newGroupId) || !studentRepository.existsById(newStudentId)) {
                        continue;
                    }
                    GroupMember next = new GroupMember();
                    next.setGroupId(newGroupId);
                    next.setStudentId(newStudentId);
                    groupMemberRepository.save(next);
                }
            }

            if (body.get("tasks") instanceof List<?> tasks) {
                longTaskRepository.deleteAll();
                longTaskRepository.flush();
                for (Object o : tasks) {
                    LongTask t = objectMapper.convertValue(o, LongTask.class);
                    Long oldId = t.getId();
                    t.setId(null);
                    LongTask saved = longTaskRepository.save(t);
                    if (oldId != null) {
                        taskIdMap.put(oldId, saved.getId());
                    }
                }
            }

            if (body.get("checkRecords") instanceof List<?> records) {
                checkRecordRepository.deleteAll();
                checkRecordRepository.flush();
                for (Object o : records) {
                    CheckRecord r = objectMapper.convertValue(o, CheckRecord.class);
                    Long oldTaskId = r.getTaskId();
                    r.setId(null);
                    if (oldTaskId != null) {
                        Long newTaskId = taskIdMap.get(oldTaskId);
                        r.setTaskId(newTaskId != null ? newTaskId : null);
                    }
                    checkRecordRepository.save(r);
                }
            }

            if (body.get("announcements") instanceof List<?> items) {
                announcementVersionRepository.deleteAll();
                announcementVersionRepository.flush();
                announcementFileRepository.deleteAll();
                announcementFileRepository.flush();
                announcementRepository.deleteAll();
                announcementRepository.flush();
                for (Object o : items) {
                    Announcement a = objectMapper.convertValue(o, Announcement.class);
                    Long oldId = a.getId();
                    a.setId(null);
                    Announcement saved = announcementRepository.save(a);
                    if (oldId != null) {
                        announcementIdMap.put(oldId, saved.getId());
                    }
                }
            }

            if (body.get("announcementVersions") instanceof List<?> items) {
                for (Object o : items) {
                    AnnouncementVersion v = objectMapper.convertValue(o, AnnouncementVersion.class);
                    v.setId(null);
                    Long oldAnnId = v.getAnnouncementId();
                    Long newAnnId = announcementIdMap.getOrDefault(oldAnnId, oldAnnId);
                    if (!announcementRepository.existsById(newAnnId)) {
                        continue;
                    }
                    v.setAnnouncementId(newAnnId);
                    announcementVersionRepository.save(v);
                }
            }

            if (body.get("announcementFiles") instanceof List<?> items) {
                for (Object o : items) {
                    AnnouncementFile f = objectMapper.convertValue(o, AnnouncementFile.class);
                    f.setId(null);
                    Long oldAnnId = f.getAnnouncementId();
                    Long newAnnId = announcementIdMap.getOrDefault(oldAnnId, oldAnnId);
                    if (!announcementRepository.existsById(newAnnId)) {
                        continue;
                    }
                    f.setAnnouncementId(newAnnId);
                    announcementFileRepository.save(f);
                }
            }

            if (body.get("leaveRecords") instanceof List<?> items) {
                leaveRecordRepository.deleteAll();
                leaveRecordRepository.flush();
                for (Object o : items) {
                    LeaveRecord r = objectMapper.convertValue(o, LeaveRecord.class);
                    r.setId(null);
                    Long oldStudentId = r.getStudentId();
                    Long newStudentId = studentIdMap.getOrDefault(oldStudentId, oldStudentId);
                    if (!studentRepository.existsById(newStudentId)) {
                        continue;
                    }
                    r.setStudentId(newStudentId);
                    leaveRecordRepository.save(r);
                }
            }

            if (body.get("scholarshipBatches") instanceof List<?> items) {
                scholarshipAwardRepository.deleteAll();
                scholarshipAwardRepository.flush();
                scholarshipBatchRepository.deleteAll();
                scholarshipBatchRepository.flush();
                for (Object o : items) {
                    ScholarshipBatch b = objectMapper.convertValue(o, ScholarshipBatch.class);
                    Long oldId = b.getId();
                    b.setId(null);
                    ScholarshipBatch saved = scholarshipBatchRepository.save(b);
                    if (oldId != null) {
                        scholarshipBatchIdMap.put(oldId, saved.getId());
                    }
                }
            }

            if (body.get("scholarshipAwards") instanceof List<?> items) {
                for (Object o : items) {
                    ScholarshipAward a = objectMapper.convertValue(o, ScholarshipAward.class);
                    a.setId(null);
                    Long oldBatchId = a.getBatchId();
                    Long newBatchId = scholarshipBatchIdMap.getOrDefault(oldBatchId, oldBatchId);
                    Long oldStudentId = a.getStudentId();
                    Long newStudentId = studentIdMap.getOrDefault(oldStudentId, oldStudentId);
                    if (!scholarshipBatchRepository.existsById(newBatchId)
                            || !studentRepository.existsById(newStudentId)) {
                        continue;
                    }
                    a.setBatchId(newBatchId);
                    a.setStudentId(newStudentId);
                    scholarshipAwardRepository.save(a);
                }
            }

            if (body.get("locked") instanceof Boolean locked) {
                studentService.setLocked(locked);
            } else if (body.get("locked") instanceof String s) {
                studentService.setLocked(Boolean.parseBoolean(s));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("students", studentRepository.count());
            result.put("groups", groupRepository.count());
            result.put("groupMembers", groupMemberRepository.count());
            result.put("tasks", longTaskRepository.count());
            result.put("checkRecords", checkRecordRepository.count());
            result.put("announcements", announcementRepository.count());
            result.put("leaveRecords", leaveRecordRepository.count());
            result.put("scholarshipBatches", scholarshipBatchRepository.count());
            return ApiResponse.ok(result);
        } catch (Exception e) {
            return ApiResponse.fail(400, "导入失败：" + e.getMessage());
        }
    }
}
