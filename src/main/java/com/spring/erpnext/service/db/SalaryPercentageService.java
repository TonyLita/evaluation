package com.spring.erpnext.service.db;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SalaryPercentageService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public boolean insertPercentage(String description, double percentage, int annee, List<Integer> moisAffectes) {
        try {
            String moisJson = moisAffectes.toString();
            String moisDisplay = generateMoisDisplay(moisAffectes);
            
            String sql = """
                INSERT INTO salary_percentage (description, percentage, annee, mois_affectes, mois_display, created_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                """;
            
            int rows = jdbcTemplate.update(sql, description, percentage, annee, moisJson, moisDisplay);
            return rows > 0;
        } catch (Exception e) {
            System.err.println("❌ Erreur insertion pourcentage: " + e.getMessage());
            return false;
        }
    }

    public List<Map<String, Object>> getAllPercentages() {
        try {
            String sql = """
                SELECT id, description, percentage, annee, mois_affectes, mois_display, created_at 
                FROM salary_percentage 
                ORDER BY annee DESC, created_at DESC
                """;
            
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            System.err.println("❌ Erreur récupération pourcentages: " + e.getMessage());
            return List.of();
        }
    }

    public double getPercentageForMonth(int year, int month) {
        try {
            String sql = """
                SELECT percentage 
                FROM salary_percentage 
                WHERE annee = ? 
                AND JSON_CONTAINS(mois_affectes, ?)
                ORDER BY created_at DESC 
                LIMIT 1
                """;
            
            String monthStr = String.valueOf(month);
            List<Double> results = jdbcTemplate.queryForList(sql, Double.class, year, monthStr);
            
            return results.isEmpty() ? 0.0 : results.get(0);
        } catch (Exception e) {
            System.err.println("❌ Erreur récupération pourcentage pour " + year + "-" + month + ": " + e.getMessage());
            return 0.0;
        }
    }

    public boolean deletePercentage(int id) {
        try {
            String sql = "DELETE FROM salary_percentage WHERE id = ?";
            int rows = jdbcTemplate.update(sql, id);
            return rows > 0;
        } catch (Exception e) {
            System.err.println("❌ Erreur suppression pourcentage: " + e.getMessage());
            return false;
        }
    }

    private String generateMoisDisplay(List<Integer> moisAffectes) {
        String[] moisNoms = {"", "Jan", "Fév", "Mar", "Avr", "Mai", "Jun", 
                            "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"};
        
        StringBuilder display = new StringBuilder();
        for (int i = 0; i < moisAffectes.size(); i++) {
            if (i > 0) display.append(", ");
            display.append(moisNoms[moisAffectes.get(i)]);
        }
        return display.toString();
    }

    public void createTableIfNotExists() {
        try {
            // Vérifier si la table existe sans essayer de la créer automatiquement
            String checkSql = "SELECT COUNT(*) FROM salary_percentage LIMIT 1";
            jdbcTemplate.queryForObject(checkSql, Integer.class);
            System.out.println("✅ Table salary_percentage existe et est accessible");
        } catch (Exception e) {
            System.err.println("⚠️ Table salary_percentage inaccessible: " + e.getMessage());
            System.err.println("📋 Veuillez créer la table manuellement avec les permissions appropriées");
        }
    }
}