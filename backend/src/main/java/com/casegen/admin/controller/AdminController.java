package com.casegen.admin.controller;

import com.casegen.admin.dto.AdminProjectMembershipResponse;
import com.casegen.admin.dto.AdminProjectResponse;
import com.casegen.admin.dto.AdminUserResponse;
import com.casegen.admin.service.AdminQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminQueryService adminQueryService;

    public AdminController(AdminQueryService adminQueryService) {
        this.adminQueryService = adminQueryService;
    }

    @GetMapping("/users")
    public List<AdminUserResponse> listUsers() {
        return adminQueryService.listUsers();
    }

    @GetMapping("/projects")
    public List<AdminProjectResponse> listProjects() {
        return adminQueryService.listProjects();
    }

    @GetMapping("/project-memberships")
    public List<AdminProjectMembershipResponse> listProjectMemberships() {
        return adminQueryService.listProjectMemberships();
    }
}
