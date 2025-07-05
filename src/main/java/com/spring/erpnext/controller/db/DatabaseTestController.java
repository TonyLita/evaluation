package com.spring.erpnext.controller.db;

import com.spring.erpnext.service.db.DatabaseTestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/db")
public class DatabaseTestController {

    @Autowired
    private DatabaseTestService databaseTestService;

    @GetMapping("/test")
    public String testDatabase(Model model, HttpSession session) {
        String sid = (String) session.getAttribute("sid");
        if (sid == null || sid.isEmpty()) {
            return "redirect:/login";
        }

        String username = (String) session.getAttribute("username");
        model.addAttribute("username", username);
        model.addAttribute("page", "db-test");

        String version = databaseTestService.testConnection();
        int employeeCount = databaseTestService.countEmployees();
        
        model.addAttribute("version", version);
        model.addAttribute("employeeCount", employeeCount);
        model.addAttribute("employees", databaseTestService.getFirstEmployees(10));
        model.addAttribute("tables", databaseTestService.getAllTables());

        return "layout/base";
    }
}