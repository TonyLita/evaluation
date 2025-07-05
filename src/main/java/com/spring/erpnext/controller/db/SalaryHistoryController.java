package com.spring.erpnext.controller.db;

import com.spring.erpnext.service.db.SalaryHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/db")
public class SalaryHistoryController {

    @Autowired
    private SalaryHistoryService salaryHistoryService;

    @GetMapping("/salary-history")
    public String salaryHistory(
            @RequestParam(value = "year", required = false, defaultValue = "2025") int year,
            @RequestParam(value = "month", required = false) Integer month,
            Model model,
            HttpSession session) {

        String sid = (String) session.getAttribute("sid");
        if (sid == null || sid.isEmpty()) {
            return "redirect:/login";
        }

        String username = (String) session.getAttribute("username");
        model.addAttribute("username", username);
        model.addAttribute("page", "db-salary-history");
        model.addAttribute("selectedYear", year);
        model.addAttribute("selectedMonth", month);

        List<Map<String, Object>> modifications;
        if (month != null) {
            modifications = salaryHistoryService.getSalaryModificationsByYearMonth(year, month);
        } else {
            modifications = salaryHistoryService.getSalaryModificationsByYear(year);
        }

        Map<String, Object> stats = salaryHistoryService.getSalaryStatsByYear(year);
        List<Map<String, Object>> monthlyData = salaryHistoryService.getMonthlyAggregation(year);

        model.addAttribute("modifications", modifications);
        model.addAttribute("stats", stats);
        model.addAttribute("monthlyData", monthlyData);

        return "layout/base";
    }
}