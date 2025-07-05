package com.spring.erpnext.service.db;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseTestService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public String testConnection() {
        try {
            return jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        } catch (Exception e) {
            return "Erreur: " + e.getMessage();
        }
    }

    public int countEmployees() {
        try {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tabEmployee", Integer.class);
        } catch (Exception e) {
            return -1;
        }
    }

    public List<Map<String, Object>> getFirstEmployees(int limit) {
        try {
            String sql = "SELECT name, first_name, last_name, company FROM tabEmployee LIMIT " + limit;
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<String> getAllTables() {
        try {
            return jdbcTemplate.queryForList("SHOW TABLES", String.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}