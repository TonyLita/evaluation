package com.spring.erpnext.service.db;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SalaryHistoryService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getSalaryModificationsByYear(int year) {
        String sql = """
            SELECT 
                ssa.employee,
                e.first_name,
                e.last_name,
                ssa.base,
                ssa.from_date,
                ssa.company,
                ssa.salary_structure,
                ssa.creation,
                MONTH(ssa.from_date) as month_num,
                MONTHNAME(ssa.from_date) as month_name
            FROM `tabSalary Structure Assignment` ssa
            LEFT JOIN tabEmployee e ON ssa.employee = e.name
            WHERE YEAR(ssa.from_date) = ? 
            AND ssa.docstatus = 1
            ORDER BY ssa.from_date DESC, ssa.employee
            """;
        
        return jdbcTemplate.queryForList(sql, year);
    }

    public List<Map<String, Object>> getSalaryModificationsByYearMonth(int year, int month) {
        String sql = """
            SELECT 
                ssa.employee,
                e.first_name,
                e.last_name,
                ssa.base,
                ssa.from_date,
                ssa.company,
                ssa.salary_structure,
                ssa.creation
            FROM `tabSalary Structure Assignment` ssa
            LEFT JOIN tabEmployee e ON ssa.employee = e.name
            WHERE YEAR(ssa.from_date) = ? 
            AND MONTH(ssa.from_date) = ?
            AND ssa.docstatus = 1
            ORDER BY ssa.from_date DESC, ssa.employee
            """;
        
        return jdbcTemplate.queryForList(sql, year, month);
    }

    public Map<String, Object> getSalaryStatsByYear(int year) {
        String sql = """
            SELECT 
                COUNT(*) as total_modifications,
                COUNT(DISTINCT employee) as employees_affected,
                AVG(base) as average_salary,
                MIN(base) as min_salary,
                MAX(base) as max_salary,
                SUM(base) as total_salary
            FROM `tabSalary Structure Assignment`
            WHERE YEAR(from_date) = ? 
            AND docstatus = 1
            """;
        
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, year);
        return result.isEmpty() ? Map.of() : result.get(0);
    }

    public List<Map<String, Object>> getMonthlyAggregation(int year) {
        String sql = """
            SELECT 
                MONTH(from_date) as month_num,
                MONTHNAME(from_date) as month_name,
                COUNT(*) as modifications_count,
                COUNT(DISTINCT employee) as employees_count,
                AVG(base) as avg_salary,
                SUM(base) as total_salary
            FROM `tabSalary Structure Assignment`
            WHERE YEAR(from_date) = ? 
            AND docstatus = 1
            GROUP BY MONTH(from_date), MONTHNAME(from_date)
            ORDER BY MONTH(from_date)
            """;
        
        return jdbcTemplate.queryForList(sql, year);
    }
}