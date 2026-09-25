package com.classmgmt.dto;

import java.util.ArrayList;
import java.util.List;

public class StudentsReplaceRequest {
    private Boolean locked;
    private List<Item> students = new ArrayList<>();

    public Boolean getLocked() {
        return locked;
    }

    public void setLocked(Boolean locked) {
        this.locked = locked;
    }

    public List<Item> getStudents() {
        return students;
    }

    public void setStudents(List<Item> students) {
        this.students = students;
    }

    public static class Item {
        private Long id;
        private String name;
        private String studentNo;
        private Integer sortOrder;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getStudentNo() {
            return studentNo;
        }

        public void setStudentNo(String studentNo) {
            this.studentNo = studentNo;
        }

        public Integer getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }
    }
}
