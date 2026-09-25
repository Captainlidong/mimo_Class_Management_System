package com.classmgmt.web;

import com.classmgmt.dto.CheckRequest;
import com.classmgmt.dto.CheckResultDTO;
import com.classmgmt.service.CheckService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/check")
public class CheckController {

    private final CheckService checkService;

    public CheckController(CheckService checkService) {
        this.checkService = checkService;
    }

    @PostMapping
    public ApiResponse<CheckResultDTO> check(@Valid @RequestBody CheckRequest request) {
        return ApiResponse.ok(checkService.check(request));
    }
}
