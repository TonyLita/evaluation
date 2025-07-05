package com.spring.erpnext.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.erpnext.model.BaseSalary;
import com.spring.erpnext.service.db.SalaryPercentageService;

import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
@Service
public class BaseSalaryService {

    private static final String BASE_URL = "http://erpnext.localhost:8003";

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SalaryPercentageService salaryPercentageService;

    public boolean insertBaseSalary(BaseSalary baseSalary, HttpSession session) {
        String sid = (String) session.getAttribute("sid");
        if (sid == null || sid.isEmpty()) {
            System.err.println("❌ Session non authentifiée");
            return false;
        }

        try {
            if (!employeeExists(session, baseSalary.getEmployee())) {
                System.err.println("❌ Employé inexistant: " + baseSalary.getEmployee());
                return false;
            }

            String salaryStructure = getDefaultSalaryStructure(session, baseSalary.getCompany());
            if (salaryStructure == null) {
                System.err
                        .println("❌ Aucune structure salariale trouvée pour l'entreprise: " + baseSalary.getCompany());
                return false;
            }

            // 3️⃣ Préparer le payload pour Salary Structure Assignment
            Map<String, Object> payload = new HashMap<>();
            payload.put("employee", baseSalary.getEmployee());
            payload.put("company", baseSalary.getCompany());
            payload.put("from_date", baseSalary.getFrom_Date());
            payload.put("base", baseSalary.getAmount());
            payload.put("salary_structure", salaryStructure);
            payload.put("docstatus", 1); // Soumis

            if (baseSalary.getRemark() != null && !baseSalary.getRemark().trim().isEmpty()) {
                payload.put("remarks", baseSalary.getRemark());
            }

            // 4️⃣ Convertir en JSON
            String json = objectMapper.writeValueAsString(payload);

            System.out.println("📤 Création assignment avec payload: " + json);

            // 5️⃣ Préparer les en-têtes HTTP
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Cookie", "sid=" + sid);
            headers.set("Expect", ""); // Pour éviter les erreurs 417

            HttpEntity<String> entity = new HttpEntity<>(json, headers);

            // 6️⃣ Envoyer la requête POST
            String url = BASE_URL + "/api/resource/Salary%20Structure%20Assignment";

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            // 7️⃣ Vérifier la réponse
            if (response.getStatusCode() == HttpStatus.OK ||
                    response.getStatusCode() == HttpStatus.CREATED) {

                System.out.println("✅ Salaire de base créé avec succès pour " + baseSalary.getEmployee());
                return true;

            } else {
                System.err.println("❌ Échec création salaire (code " + response.getStatusCode() + ")");
                System.err.println("📋 Réponse: " + response.getBody());
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ Erreur lors de l'insertion du salaire de base: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Vérifier si un employé existe dans ERPNext
     */
    private boolean employeeExists(HttpSession session, String employeeId) {
        String sid = (String) session.getAttribute("sid");
        if (sid == null)
            return false;

        try {
            String url = BASE_URL + "/api/resource/Employee/" + employeeId;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Cookie", "sid=" + sid);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            return response.getStatusCode() == HttpStatus.OK;

        } catch (Exception e) {
            System.err.println("❌ Erreur vérification employé " + employeeId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Récupérer la structure salariale par défaut pour une entreprise
     */
    private String getDefaultSalaryStructure(HttpSession session, String company) {
        String sid = (String) session.getAttribute("sid");
        if (sid == null)
            return null;

        try {
            // Filtre pour structures actives de l'entreprise
            String filtersJson = "[[\"Salary Structure\",\"company\",\"=\",\"" + company + "\"]," +
                    "[\"Salary Structure\",\"docstatus\",\"=\",\"1\"]]";

            URI uri = UriComponentsBuilder
                    .fromHttpUrl(BASE_URL + "/api/resource/Salary Structure")
                    .queryParam("fields", "[\"name\",\"is_default\"]")
                    .queryParam("filters", filtersJson)
                    .queryParam("order_by", "is_default desc, creation desc")
                    .queryParam("limit_page_length", "1")
                    .build()
                    .encode()
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Cookie", "sid=" + sid);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {
                };
                Map<String, Object> result = objectMapper.readValue(response.getBody(), typeRef);

                if (result.containsKey("data")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> structures = (List<Map<String, Object>>) result.get("data");

                    if (!structures.isEmpty()) {
                        Map<String, Object> structure = structures.get(0);
                        String structureName = (String) structure.get("name");
                        System.out.println("🏗️ Structure salariale trouvée: " + structureName);
                        return structureName;
                    }
                }
            }

            // Si aucune structure trouvée, utiliser une valeur par défaut
            System.out.println("⚠️ Aucune structure trouvée pour " + company + ", utilisation de STRUCT1");
            return "STRUCT1";

        } catch (Exception e) {
            System.err.println("❌ Erreur récupération structure salariale: " + e.getMessage());
            return "STRUCT1"; // Valeur par défaut
        }
    }

    public boolean insertBaseSalariesBatch(List<BaseSalary> baseSalaries, HttpSession session) {
        boolean allSuccess = true;
        int successCount = 0;
        int failCount = 0;

        System.out.println("🚀 Début insertion en lot de " + baseSalaries.size() + " salaires de base");

        for (BaseSalary baseSalary : baseSalaries) {
            try {
                boolean success = insertBaseSalary(baseSalary, session);

                if (success) {
                    successCount++;
                    System.out.println("✅ Succès pour " + baseSalary.getEmployee());
                } else {
                    failCount++;
                    allSuccess = false;
                    System.err.println("❌ Échec pour " + baseSalary.getEmployee());
                }

                // Pause entre les insertions pour éviter la surcharge
                Thread.sleep(500);

            } catch (Exception e) {
                failCount++;
                allSuccess = false;
                System.err.println("❌ Erreur pour " + baseSalary.getEmployee() + ": " + e.getMessage());
            }
        }

        System.out.println("📊 Résultat insertion lot: " + successCount + " succès, " + failCount + " échecs");
        return allSuccess;
    }

    /**
     * Vérifier si un employé a déjà un salaire de base actif
     */
    public boolean hasActiveSalaryAssignment(HttpSession session, String employeeId) {
        String sid = (String) session.getAttribute("sid");
        if (sid == null)
            return false;

        try {
            String filtersJson = "[[\"Salary Structure Assignment\",\"employee\",\"=\",\"" + employeeId + "\"]," +
                    "[\"Salary Structure Assignment\",\"docstatus\",\"=\",\"1\"]]";

            URI uri = UriComponentsBuilder
                    .fromHttpUrl(BASE_URL + "/api/resource/Salary Structure Assignment")
                    .queryParam("fields", "[\"name\",\"from_date\"]")
                    .queryParam("filters", filtersJson)
                    .queryParam("order_by", "from_date desc")
                    .queryParam("limit_page_length", "1")
                    .build()
                    .encode()
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Cookie", "sid=" + sid);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {
                };
                Map<String, Object> result = objectMapper.readValue(response.getBody(), typeRef);

                if (result.containsKey("data")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> assignments = (List<Map<String, Object>>) result.get("data");

                    boolean hasActive = !assignments.isEmpty();
                    System.out.println("🔍 Employé " + employeeId + " a un assignment actif: " + hasActive);
                    return hasActive;
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Erreur vérification assignment actif: " + e.getMessage());
        }

        return false;
    }

    public boolean genererSalaire(
            String employeRef,
            String dateDebut,
            String dateFin,
            String company,
            String salaryStructure,
            double montant,
            HttpSession session) {

        String sid = (String) session.getAttribute("sid");
        if (sid == null || sid.isEmpty()) {
            System.err.println("❌ Session non authentifiée");
            return false;
        }

        try {
            if (!employeeExists(session, employeRef)) {
                System.err.println("❌ Employé inexistant : " + employeRef);
                return false;
            }

            if (salaryStructure == null || salaryStructure.isEmpty()) {
                System.err.println("❌ Structure salariale non spécifiée");
                return false;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Cookie", "sid=" + sid);
            headers.set("Expect", "");

            if (montant == 0.0) {
                montant = recupererDernierSalaireBaseAvantDate(employeRef, dateDebut, headers);
                if (montant == -1.0) {
                    System.err.println("❌ Aucun historique salarial avant " + dateDebut + " pour " + employeRef);
                    return false;
                }
                if (montant == 0.0) {
                    System.err.println("❌ Salaire de base introuvable pour " + employeRef);
                    return false;
                }
            }

            LocalDate start = LocalDate.parse(dateDebut).withDayOfMonth(1);
            LocalDate end = LocalDate.parse(dateFin).withDayOfMonth(1);

            while (!start.isAfter(end)) {
                LocalDate moisDebut = start.withDayOfMonth(1);
                LocalDate moisFin = start.withDayOfMonth(start.lengthOfMonth());

                // Vérifier existence du Salary Structure Assignment
                if (!existsSalaryStructureAssignment(employeRef, moisDebut, headers)) {
                    Map<String, Object> assignmentPayload = new HashMap<>();
                    assignmentPayload.put("employee", employeRef);
                    assignmentPayload.put("company", company);
                    assignmentPayload.put("from_date", moisDebut.toString());
                    assignmentPayload.put("salary_structure", salaryStructure);
                    assignmentPayload.put("base", montant);
                    assignmentPayload.put("docstatus", 1);

                    String assignmentJson = objectMapper.writeValueAsString(assignmentPayload);
                    HttpEntity<String> assignmentEntity = new HttpEntity<>(assignmentJson, headers);

                    ResponseEntity<String> assignmentResponse = restTemplate.postForEntity(
                            BASE_URL + "/api/resource/Salary Structure Assignment",
                            assignmentEntity,
                            String.class);

                    if (!assignmentResponse.getStatusCode().is2xxSuccessful()) {
                        System.err.println("❌ Échec SSA pour " + moisDebut + ": " + assignmentResponse.getBody());
                        return false;
                    }
                } else {
                    System.out.println("⚠️ SSA déjà existant pour " + moisDebut + ", création ignorée.");
                }

                // Vérifier existence du Salary Slip
                if (!existsSalarySlip(employeRef, moisDebut, moisFin, headers)) {
                    Map<String, Object> slipPayload = new HashMap<>();
                    slipPayload.put("employee", employeRef);
                    slipPayload.put("start_date", moisDebut.toString());
                    slipPayload.put("end_date", moisFin.toString());
                    slipPayload.put("salary_structure", salaryStructure);
                    slipPayload.put("company", company);
                    slipPayload.put("docstatus", 1);

                    String slipJson = objectMapper.writeValueAsString(slipPayload);
                    HttpEntity<String> slipEntity = new HttpEntity<>(slipJson, headers);

                    ResponseEntity<String> slipResponse = restTemplate.postForEntity(
                            BASE_URL + "/api/resource/Salary Slip",
                            slipEntity,
                            String.class);

                    if (!slipResponse.getStatusCode().is2xxSuccessful()) {
                        System.err.println("❌ Échec fiche de paie pour " + moisDebut + ": " + slipResponse.getBody());
                        return false;
                    }
                } else {
                    System.out.println("⚠️ Fiche de paie déjà existante pour " + moisDebut + ", création ignorée.");
                }

                System.out.println("✅ Salaire traité pour " + employeRef + " - Mois : " + moisDebut.getMonth());
                start = start.plusMonths(1);
            }

            return true;

        } catch (Exception e) {
            System.err.println("❌ Erreur génération salaire: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean existsSalaryStructureAssignment(String employeRef, LocalDate moisDebut, HttpHeaders headers) {
        String url = BASE_URL + "/api/resource/Salary Structure Assignment?filters="
                + "[[\"employee\", \"=\", \"" + employeRef + "\"],"
                + "[\"from_date\", \"=\", \"" + moisDebut.toString() + "\"]]";

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            try {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.get("data");
                return data != null && data.size() > 0;
            } catch (IOException e) {
                System.err.println("❌ Erreur parsing JSON SSA: " + e.getMessage());
                e.printStackTrace();
                return false; // Ou true selon ta logique, mais false semble plus sûr
            }
        } else {
            System.err.println("❌ Erreur vérification SSA: " + response.getBody());
            return false;
        }
    }

    private boolean existsSalarySlip(String employeRef, LocalDate moisDebut, LocalDate moisFin, HttpHeaders headers) {
        String url = BASE_URL + "/api/resource/Salary Slip?filters="
                + "[[\"employee\", \"=\", \"" + employeRef + "\"],"
                + "[\"start_date\", \"=\", \"" + moisDebut.toString() + "\"],"
                + "[\"end_date\", \"=\", \"" + moisFin.toString() + "\"]]";

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            try {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.get("data");
                return data != null && data.size() > 0;
            } catch (IOException e) {
                System.err.println("❌ Erreur parsing JSON Salary Slip: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        } else {
            System.err.println("❌ Erreur vérification Salary Slip: " + response.getBody());
            return false;
        }
    }

    // private double recupererDernierSalaireBase(String employeRef, HttpHeaders
    // headers) {
    // try {
    // String url = BASE_URL + "/api/resource/Salary Structure Assignment?filters="
    // + "[[\"employee\", \"=\", \"" + employeRef + "\"]]"
    // + "&fields=[\"base\", \"from_date\"]"
    // + "&limit_page_length=1"
    // + "&order_by=from_date desc";

    // HttpEntity<String> entity = new Http Entity<>(headers);
    // ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET,
    // entity, String.class);

    // System.out.println("Réponse JSON : " + response.getBody()); // DEBUG

    // if (response.getStatusCode().is2xxSuccessful()) {
    // JsonNode root = objectMapper.readTree(response.getBody());
    // JsonNode data = root.get("data");
    // if (data != null && data.isArray() && data.size() > 0) {
    // JsonNode dernierSSA = data.get(0);
    // System.out.println("Dernier SSA JSON: " + dernierSSA.toString()); // DEBUG
    // return dernierSSA.has("base") ? dernierSSA.get("base").asDouble() : 0.0;
    // } else {
    // System.err.println("❌ 'data' est vide ou pas un tableau");
    // }
    // } else {
    // System.err.println("❌ Erreur récupération dernier SSA: " +
    // response.getBody());
    // }
    // } catch (Exception e) {
    // System.err.println("❌ Exception récupération dernier SSA: " +
    // e.getMessage());
    // e.printStackTrace();
    // }
    // }

    private double recupererDernierSalaireBaseAvantDate(String employeRef, String dateLimite, HttpHeaders headers) {
        try {
            String url = BASE_URL + "/api/resource/Salary Structure Assignment?filters="
                    + "[[\"employee\", \"=\", \"" + employeRef + "\"],"
                    + "[\"from_date\", \"<\", \"" + dateLimite + "\"]]"
                    + "&fields=[\"base\", \"from_date\"]"
                    + "&limit_page_length=1"
                    + "&order_by=from_date desc";

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.get("data");
                if (data != null && data.isArray() && data.size() > 0) {
                    JsonNode dernierSSA = data.get(0);
                    return dernierSSA.has("base") ? dernierSSA.get("base").asDouble() : 0.0;
                } else {
                    System.out.println("ℹ️ Aucun Salary Structure Assignment avant " + dateLimite);
                    return -1.0; // Aucun historique avant
                }
            } else {
                System.err.println("❌ Erreur récupération SSA: " + response.getBody());
            }
        } catch (Exception e) {
            System.err.println("❌ Exception récupération SSA: " + e.getMessage());
            e.printStackTrace();
        }
        return -1.0;
    }

    public List<BaseSalary> getAllBaseSalaries(HttpSession session) {
        String sid = (String) session.getAttribute("sid");
        if (sid == null || sid.isEmpty()) {
            throw new IllegalArgumentException("SID dans la session est nul ou vide");
        }

        String url = BASE_URL + "/api/resource/Salary Structure?limit_page_length=1000";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", "sid=" + sid);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<BaseSalaryApiResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                BaseSalaryApiResponse.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return response.getBody().getData();
        } else {
            throw new RuntimeException("Erreur lors de la récupération des salaires de base");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BaseSalaryApiResponse {
        private List<BaseSalary> data;

        public List<BaseSalary> getData() {
            return data;
        }

        public void setData(List<BaseSalary> data) {
            this.data = data;
        }
    }

    public Map<String, String> getSalaryStructureAndCompany(HttpSession session, String employeeId) {
        String sid = (String) session.getAttribute("sid");
        if (sid == null)
            return null;

        try {
            String filtersJson = "[[\"Salary Structure Assignment\",\"employee\",\"=\",\"" + employeeId + "\"]]";

            URI uri = UriComponentsBuilder
                    .fromHttpUrl(BASE_URL + "/api/resource/Salary Structure Assignment")
                    .queryParam("fields", "[\"salary_structure\",\"company\",\"from_date\"]")
                    .queryParam("filters", filtersJson)
                    .queryParam("order_by", "from_date desc")
                    .queryParam("limit_page_length", "1")
                    .build()
                    .encode()
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Cookie", "sid=" + sid);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {
                };
                Map<String, Object> result = objectMapper.readValue(response.getBody(), typeRef);

                if (result.containsKey("data")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> assignments = (List<Map<String, Object>>) result.get("data");

                    if (!assignments.isEmpty()) {
                        Map<String, Object> assignment = assignments.get(0);
                        String salaryStructure = assignment.get("salary_structure") != null
                                ? assignment.get("salary_structure").toString()
                                : null;
                        String company = assignment.get("company") != null ? assignment.get("company").toString()
                                : null;

                        Map<String, String> infos = new HashMap<>();
                        infos.put("salary_structure", salaryStructure);
                        infos.put("company", company);

                        System.out.println("✅ Salary Structure : " + salaryStructure + ", Company : " + company);
                        return infos;
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Erreur récupération Salary Structure et Company : " + e.getMessage());
        }

        return null;
    }

    public boolean regenererSalaireAvecEcraser(
            String employeRef,
            String dateDebut,
            String dateFin,
            String company,
            String salaryStructure,
            double montant,
            String ecraser,
            HttpSession session) {

        System.out.println("🔔 Début de regenererSalaireAvecEcraser");
        System.out.println("Paramètres reçus : employe=" + employeRef + ", dateDebut=" + dateDebut + ", dateFin="
                + dateFin + ", ecraser=" + ecraser + ", montant=" + montant);

        String sid = (String) session.getAttribute("sid");
        if (sid == null || sid.isEmpty()) {
            System.err.println("❌ Session non authentifiée");
            return false;
        }

        try {
            if (!employeeExists(session, employeRef)) {
                System.err.println("❌ Employé inexistant : " + employeRef);
                return false;
            }

            if (salaryStructure == null || salaryStructure.isEmpty()) {
                System.err.println("❌ Structure salariale non spécifiée");
                return false;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Cookie", "sid=" + sid);
            headers.set("Expect", "");

            if (montant == 0.0) {
                montant = recupererDernierSalaireBaseAvantDate(employeRef, dateDebut, headers);
                if (montant == -1.0 || montant == 0.0) {
                    System.err.println("❌ Salaire de base introuvable ou inexistant avant " + dateDebut);
                    return false;
                }
            }

            LocalDate start = LocalDate.parse(dateDebut).withDayOfMonth(1);
            LocalDate end = LocalDate.parse(dateFin).withDayOfMonth(1);

            boolean forcer = ecraser != null && ecraser.equalsIgnoreCase("oui");
            System.out.println("Paramètre ecraser détecté : " + forcer);

            if (forcer) {
                System.out.println("🗑️ Suppression SSA et Salary Slip entre " + start + " et " + end
                        + " pour l'employé " + employeRef);
                supprimerSalaryStructureAssignmentEntreDeuxDates(employeRef, start, end, headers);
                supprimerSalarySlipEntreDeuxDates(employeRef, start, end, headers);
                System.out.println("✅ Suppression terminée");
            } else {
                System.out.println("⚠️ Suppression ignorée (paramètre ecraser != 'oui')");
            }

            System.out.println("🚀 Création SSA et Salary Slip du " + start + " au " + end);

            while (!start.isAfter(end)) {
                LocalDate moisDebut = start.withDayOfMonth(1);
                LocalDate moisFin = start.withDayOfMonth(start.lengthOfMonth());

                System.out.println("📅 Création pour : " + moisDebut.getMonth() + " " + moisDebut.getYear());

                // 🔧 NOUVEAU : Récupérer le pourcentage pour ce mois
                double percentage = salaryPercentageService.getPercentageForMonth(
                    moisDebut.getYear(), 
                    moisDebut.getMonthValue()
                );
                
                double montantFinal = montant;
                if (percentage != 0.0) {
                    montantFinal = montant + (montant * percentage / 100);
                    System.out.println("💰 Pourcentage appliqué: " + percentage + "% - Montant: " + montant + " → " + montantFinal);
                }

                creerSSA(employeRef, company, salaryStructure, montantFinal, moisDebut, headers);
                creerSalarySlip(employeRef, company, salaryStructure, moisDebut, moisFin, headers);

                start = start.plusMonths(1);
            }

            System.out.println("🎉 Génération terminée avec succès !");
            return true;

        } catch (Exception e) {
            System.err.println("❌ Erreur génération salaire : " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void supprimerSalaryStructureAssignmentEntreDeuxDates(String employeRef, LocalDate dateDebut,
            LocalDate dateFin, HttpHeaders headers) {
        String url = BASE_URL + "/api/resource/Salary Structure Assignment?filters="
                + "[[\"employee\", \"=\", \"" + employeRef + "\"],"
                + "[\"from_date\", \">=\", \"" + dateDebut.toString() + "\"],"
                + "[\"from_date\", \"<=\", \"" + dateFin.toString() + "\"]]";

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            try {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.get("data");

                if (data != null) {
                    for (JsonNode item : data) {
                        String name = item.get("name").asText();

                        String cancelUrl = BASE_URL + "/api/resource/Salary Structure Assignment/" + name;
                        Map<String, Object> cancelPayload = new HashMap<>();
                        cancelPayload.put("docstatus", 2);

                        HttpEntity<String> cancelEntity = new HttpEntity<>(
                                objectMapper.writeValueAsString(cancelPayload), headers);
                        restTemplate.exchange(cancelUrl, HttpMethod.PUT, cancelEntity, String.class);

                        String deleteUrl = BASE_URL + "/api/resource/Salary Structure Assignment/" + name;
                        restTemplate.exchange(deleteUrl, HttpMethod.DELETE, entity, String.class);

                        System.out.println("🗑️ SSA supprimé : " + name);
                    }
                }
            } catch (IOException e) {
                System.err.println("❌ Erreur suppression SSA : " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void supprimerSalarySlipEntreDeuxDates(String employeRef, LocalDate dateDebut, LocalDate dateFin,
            HttpHeaders headers) {
        String url = BASE_URL + "/api/resource/Salary Slip?filters="
                + "[[\"employee\", \"=\", \"" + employeRef + "\"],"
                + "[\"start_date\", \">=\", \"" + dateDebut.toString() + "\"],"
                + "[\"start_date\", \"<=\", \"" + dateFin.toString() + "\"]]";

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            try {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.get("data");

                if (data != null) {
                    for (JsonNode item : data) {
                        String name = item.get("name").asText();

                        String cancelUrl = BASE_URL + "/api/resource/Salary Slip/" + name;
                        Map<String, Object> cancelPayload = new HashMap<>();
                        cancelPayload.put("docstatus", 2);

                        HttpEntity<String> cancelEntity = new HttpEntity<>(
                                objectMapper.writeValueAsString(cancelPayload), headers);
                        restTemplate.exchange(cancelUrl, HttpMethod.PUT, cancelEntity, String.class);

                        String deleteUrl = BASE_URL + "/api/resource/Salary Slip/" + name;
                        restTemplate.exchange(deleteUrl, HttpMethod.DELETE, entity, String.class);

                        System.out.println("🗑️ Fiche de paie supprimée : " + name);
                    }
                }
            } catch (IOException e) {
                System.err.println("❌ Erreur suppression Salary Slip : " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void creerSSA(String employeRef, String company, String salaryStructure, double montant,
            LocalDate moisDebut, HttpHeaders headers) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("employee", employeRef);
        payload.put("company", company);
        payload.put("from_date", moisDebut.toString());
        payload.put("salary_structure", salaryStructure);
        payload.put("base", montant);
        payload.put("docstatus", 1);

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                BASE_URL + "/api/resource/Salary Structure Assignment", entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            System.err.println("❌ Échec création SSA : HTTP " + response.getStatusCode());
            System.err.println("Response body : " + response.getBody());
            throw new RuntimeException("Échec création SSA");
        }
        System.out.println("✅ SSA créé pour " + moisDebut);
    }

    private void creerSalarySlip(String employeRef, String company, String salaryStructure, LocalDate moisDebut,
            LocalDate moisFin, HttpHeaders headers) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("employee", employeRef);
        payload.put("start_date", moisDebut.toString());
        payload.put("end_date", moisFin.toString());
        payload.put("salary_structure", salaryStructure);
        payload.put("company", company);
        payload.put("docstatus", 1);

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                BASE_URL + "/api/resource/Salary Slip", entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            System.err.println("❌ Échec création Salary Slip : HTTP " + response.getStatusCode());
            System.err.println("Response body : " + response.getBody());
            throw new RuntimeException("Échec création Salary Slip");
        }
        System.out.println("✅ Salary Slip créé pour " + moisDebut);
    }

    /**
     * Calculer la moyenne de TOUS les salaires de base existants dans la base
     */
    public double getAverageBaseSalary(HttpSession session) {
        String sid = (String) session.getAttribute("sid");
        if (sid == null || sid.isEmpty()) {
            System.err.println("❌ Session non authentifiée");
            return 0.0;
        }

        try {
            String url = BASE_URL + "/api/resource/Salary Structure Assignment?filters="
                    + "[[\"Salary Structure Assignment\",\"docstatus\",\"=\",\"1\"]]"
                    + "&fields=[\"base\",\"employee\",\"from_date\",\"creation\"]"
                    + "&limit_page_length=1000";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Cookie", "sid=" + sid);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.get("data");
                
                if (data != null && data.isArray() && data.size() > 0) {
                    List<Double> allBaseSalaries = new ArrayList<>();
                    
                    // 🔄 NOUVEAU : Prendre TOUS les salaires de base, pas par employé
                    for (JsonNode assignment : data) {
                        double base = assignment.has("base") ? assignment.get("base").asDouble() : 0.0;
                        String employee = assignment.has("employee") ? assignment.get("employee").asText() : "N/A";
                        String fromDate = assignment.has("from_date") ? assignment.get("from_date").asText() : "N/A";
                        
                        if (base > 0) {
                            allBaseSalaries.add(base);
                            System.out.println("📊 Salaire ajouté: " + base + " (Employé: " + employee + ", Date: " + fromDate + ")");
                        }
                    }
                    
                    // Calculer la moyenne de TOUS les salaires
                    if (!allBaseSalaries.isEmpty()) {
                        double totalSalary = allBaseSalaries.stream().mapToDouble(Double::doubleValue).sum();
                        int count = allBaseSalaries.size();
                        double average = totalSalary / count;
                        
                        System.out.println("✅ Calcul moyenne de TOUS les salaires:");
                        System.out.println("   - Total des salaires: " + totalSalary);
                        System.out.println("   - Nombre total d'assignments: " + count);
                        System.out.println("   - Moyenne calculée: " + average);
                        
                        return average;
                    } else {
                        System.out.println("⚠️ Aucun salaire de base valide trouvé");
                        return 0.0;
                    }
                } else {
                    System.out.println("⚠️ Aucune donnée trouvée dans la réponse API");
                    return 0.0;
                }
            } else {
                System.err.println("❌ Erreur récupération assignments: " + response.getStatusCode());
                System.err.println("   Réponse: " + response.getBody());
                return 0.0;
            }
        } catch (Exception e) {
            System.err.println("❌ Exception lors du calcul de la moyenne: " + e.getMessage());
            e.printStackTrace();
            return 0.0;
        }
    }

    /**
     * Récupérer tous les salaires de base avec les informations des employés
     */
    public List<Map<String, Object>> getAllBaseSalariesWithEmployeeInfo(HttpSession session) {
        String sid = (String) session.getAttribute("sid");
        if (sid == null || sid.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // Récupérer tous les Salary Structure Assignments actifs
            String url = BASE_URL + "/api/resource/Salary Structure Assignment?filters="
                    + "[[\"Salary Structure Assignment\",\"docstatus\",\"=\",\"1\"]]"
                    + "&fields=[\"base\",\"employee\",\"employee_name\",\"company\",\"salary_structure\",\"from_date\"]"
                    + "&limit_page_length=1000"
                    + "&order_by=employee asc";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Cookie", "sid=" + sid);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.get("data");
                
                List<Map<String, Object>> baseSalaries = new ArrayList<>();
                
                if (data != null && data.isArray()) {
                    // Grouper par employé pour éviter les doublons
                    Map<String, Map<String, Object>> employeeLatestSalary = new LinkedHashMap<>();
                    
                    for (JsonNode assignment : data) {
                        String employee = assignment.has("employee") ? assignment.get("employee").asText() : null;
                        String employeeName = assignment.has("employee_name") ? assignment.get("employee_name").asText() : "N/A";
                        double base = assignment.has("base") ? assignment.get("base").asDouble() : 0.0;
                        String company = assignment.has("company") ? assignment.get("company").asText() : "N/A";
                        String salaryStructure = assignment.has("salary_structure") ? assignment.get("salary_structure").asText() : "N/A";
                        String fromDate = assignment.has("from_date") ? assignment.get("from_date").asText() : "N/A";
                        
                        if (employee != null && base > 0) {
                            Map<String, Object> salaryInfo = new HashMap<>();
                            salaryInfo.put("employee", employee);
                            salaryInfo.put("employee_name", employeeName);
                            salaryInfo.put("base", base);
                            salaryInfo.put("company", company);
                            salaryInfo.put("salary_structure", salaryStructure);
                            salaryInfo.put("from_date", fromDate);
                            
                            // Prendre le plus récent par employé
                            if (!employeeLatestSalary.containsKey(employee)) {
                                employeeLatestSalary.put(employee, salaryInfo);
                            }
                        }
                    }
                    
                    baseSalaries.addAll(employeeLatestSalary.values());
                }
                
                System.out.println("✅ " + baseSalaries.size() + " salaires de base récupérés");
                return baseSalaries;
            } else {
                System.err.println("❌ Erreur récupération salaires de base: " + response.getStatusCode());
                return new ArrayList<>();
            }
        } catch (Exception e) {
            System.err.println("❌ Exception récupération salaires de base: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

}