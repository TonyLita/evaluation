package com.spring.erpnext.controller.db;

import com.spring.erpnext.service.db.SalaryPercentageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/db")
public class SalaryPercentageController {

    @Autowired
    private SalaryPercentageService salaryPercentageService;

    @GetMapping("/salary-percentage")
    public String salaryPercentage(Model model, HttpSession session) {
        String sid = (String) session.getAttribute("sid");
        if (sid == null || sid.isEmpty()) {
            return "redirect:/login";
        }

        String username = (String) session.getAttribute("username");
        model.addAttribute("username", username);
        model.addAttribute("page", "db-salary-percentage");

        salaryPercentageService.createTableIfNotExists();
        List<Map<String, Object>> percentages = salaryPercentageService.getAllPercentages();
        model.addAttribute("percentages", percentages);

        return "layout/base";
    }

    @PostMapping("/salary-percentage")
    public String addPercentage(
            @RequestParam("description") String description,
            @RequestParam("percentage") double percentage,
            @RequestParam("annee") int annee,
            @RequestParam("moisAffectes") List<Integer> moisAffectes,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String sid = (String) session.getAttribute("sid");
        if (sid == null || sid.isEmpty()) {
            return "redirect:/login";
        }

        if (moisAffectes.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "❌ Veuillez sélectionner au moins un mois");
            return "redirect:/db/salary-percentage";
        }

        boolean success = salaryPercentageService.insertPercentage(description, percentage, annee, moisAffectes);

        if (success) {
            redirectAttributes.addFlashAttribute("message", "✅ Pourcentage ajouté avec succès");
        } else {
            redirectAttributes.addFlashAttribute("error", "❌ Erreur lors de l'ajout du pourcentage");
        }

        return "redirect:/db/salary-percentage";
    }

    @GetMapping("/salary-percentage/delete/{id}")
    public String deletePercentage(@PathVariable("id") int id, RedirectAttributes redirectAttributes) {
        boolean success = salaryPercentageService.deletePercentage(id);

        if (success) {
            redirectAttributes.addFlashAttribute("message", "✅ Pourcentage supprimé avec succès");
        } else {
            redirectAttributes.addFlashAttribute("error", "❌ Erreur lors de la suppression");
        }

        return "redirect:/db/salary-percentage";
    }

    @GetMapping("/salary-percentage/api/{year}/{month}")
    @ResponseBody
    public Map<String, Object> getPercentageApi(@PathVariable int year, @PathVariable int month) {
        double percentage = salaryPercentageService.getPercentageForMonth(year, month);
        return Map.of("percentage", percentage);
    }
}