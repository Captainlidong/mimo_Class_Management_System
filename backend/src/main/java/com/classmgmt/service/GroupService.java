package com.classmgmt.service;

import com.classmgmt.domain.GroupInfo;
import com.classmgmt.domain.GroupMember;
import com.classmgmt.domain.Student;
import com.classmgmt.repo.GroupMemberRepository;
import com.classmgmt.repo.GroupRepository;
import com.classmgmt.repo.StudentRepository;
import com.classmgmt.web.GlobalExceptionHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final StudentRepository studentRepository;

    public GroupService(GroupRepository groupRepository,
                        GroupMemberRepository groupMemberRepository,
                        StudentRepository studentRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        List<GroupInfo> groups = groupRepository.findAll();
        Map<Long, Student> studentMap = studentRepository.findAll().stream()
                .collect(Collectors.toMap(Student::getId, s -> s));
        List<Map<String, Object>> result = new ArrayList<>();
        for (GroupInfo g : groups) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", g.getId());
            item.put("name", g.getName());
            item.put("remark", g.getRemark());
            List<Map<String, Object>> members = new ArrayList<>();
            for (GroupMember gm : groupMemberRepository.findByGroupId(g.getId())) {
                Student s = studentMap.get(gm.getStudentId());
                if (s == null) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("studentId", s.getId());
                m.put("name", s.getName());
                m.put("studentNo", s.getStudentNo());
                members.add(m);
            }
            item.put("members", members);
            item.put("memberCount", members.size());
            result.add(item);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        String name = body.get("name") == null ? "" : String.valueOf(body.get("name")).trim();
        if (name.isEmpty()) {
            throw GlobalExceptionHandler.badRequest("分组名称不能为空");
        }
        GroupInfo g = new GroupInfo();
        g.setName(name);
        g.setRemark(body.get("remark") == null ? null : String.valueOf(body.get("remark")));
        groupRepository.save(g);
        if (body.get("studentIds") instanceof List<?> ids) {
            setMembers(g.getId(), ids.stream().map(o -> Long.valueOf(String.valueOf(o))).toList());
        }
        return list().stream().filter(x -> g.getId().equals(x.get("id"))).findFirst().orElse(Map.of());
    }

    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> body) {
        GroupInfo g = groupRepository.findById(id)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("分组不存在"));
        if (body.containsKey("name")) {
            String name = body.get("name") == null ? "" : String.valueOf(body.get("name")).trim();
            if (name.isEmpty()) {
                throw GlobalExceptionHandler.badRequest("分组名称不能为空");
            }
            g.setName(name);
        }
        if (body.containsKey("remark")) {
            g.setRemark(body.get("remark") == null ? null : String.valueOf(body.get("remark")));
        }
        groupRepository.save(g);
        if (body.get("studentIds") instanceof List<?> ids) {
            setMembers(id, ids.stream().map(o -> Long.valueOf(String.valueOf(o))).toList());
        }
        return list().stream().filter(x -> id.equals(x.get("id"))).findFirst().orElse(Map.of());
    }

    @Transactional
    public void delete(Long id) {
        if (!groupRepository.existsById(id)) {
            throw GlobalExceptionHandler.notFound("分组不存在");
        }
        groupMemberRepository.deleteByGroupId(id);
        groupRepository.deleteById(id);
    }

    @Transactional
    public void setMembers(Long groupId, List<Long> studentIds) {
        if (!groupRepository.existsById(groupId)) {
            throw GlobalExceptionHandler.notFound("分组不存在");
        }
        groupMemberRepository.deleteByGroupId(groupId);
        groupMemberRepository.flush();
        Set<Long> unique = studentIds.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        for (Long sid : unique) {
            if (!studentRepository.existsById(sid)) {
                throw GlobalExceptionHandler.badRequest("学生不存在：" + sid);
            }
            GroupMember gm = new GroupMember();
            gm.setGroupId(groupId);
            gm.setStudentId(sid);
            groupMemberRepository.save(gm);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> checkView(Long groupId, Long recordId, HistoryService historyService) {
        Map<String, Object> group = list().stream()
                .filter(x -> groupId.equals(x.get("id")))
                .findFirst()
                .orElseThrow(() -> GlobalExceptionHandler.notFound("分组不存在"));
        var record = historyService.detail(recordId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) group.get("members");
        Set<String> participated = Set.copyOf(record.getParticipated());
        List<String> groupParticipated = new ArrayList<>();
        List<String> groupAbsent = new ArrayList<>();
        for (Map<String, Object> m : members) {
            String name = String.valueOf(m.get("name"));
            if (participated.contains(name)) {
                groupParticipated.add(name);
            } else {
                groupAbsent.add(name);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("group", group);
        result.put("recordId", recordId);
        result.put("title", record.getTitle());
        result.put("participated", groupParticipated);
        result.put("absent", groupAbsent);
        result.put("participatedCount", groupParticipated.size());
        result.put("absentCount", groupAbsent.size());
        return result;
    }
}
