package com.spring.erpnext.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spring.erpnext.model.Component;
import com.spring.erpnext.model.Deduction;
import com.spring.erpnext.model.Earning;
import com.spring.erpnext.model.SalaryAssignment;
import com.spring.erpnext.model.SalarySlip;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ComponentService {

    private static final String BASE_URL = "http://erpnext.localhost:8003";

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Component> getAllComponents(HttpSession session) {
        String sid = (String) session.getAttribute("sid");

        if (sid == null || sid.isEmpty()) {
            System.err.println("❌ Session non authentifiée");
            return Collections.emptyList();
        }

        try {
            String url = BASE_URL + "/api/resource/Salary Component?limit_page_length=1000";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Cookie", "sid=" + sid);
            headers.set("Accept", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                ComponentApiResponse apiResponse = objectMapper.readValue(response.getBody(),
                        ComponentApiResponse.class);
                return apiResponse.getData();
            } else {
                System.err.println("❌ Erreur récupération components : " + response.getBody());
            }

        } catch (Exception e) {
            System.err.println("❌ Exception récupération components : " + e.getMessage());
            e.printStackTrace();
        }

        return Collections.emptyList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComponentApiResponse {
        private List<Component> data;

        public List<Component> getData() {
            return data;
        }

        public void setData(List<Component> data) {
            this.data = data;
        }
    }

    public void regenererSalaire(String componentName, String condition, double value, double percentage, String action,
            HttpSession session) {

        String sid = (String) session.getAttribute("sid");

        if (sid == null || sid.isEmpty()) {
            System.err.println("❌ Session non authentifiée");
            return;
        }
        System.out.println("✅ SID récupéré : " + sid);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Cookie", "sid=" + sid);
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Récupération des Salary Slips
            String slipUrl = UriComponentsBuilder
                    .fromHttpUrl(BASE_URL + "/api/resource/Salary Slip")
                    .queryParam("limit_page_length", "1000")
                    .queryParam("fields", "[\"*\"]")
                    .build().toUriString();

            ResponseEntity<String> slipResponse = restTemplate.exchange(slipUrl, HttpMethod.GET, entity, String.class);

            if (!slipResponse.getStatusCode().is2xxSuccessful()) {
                System.err.println("❌ Erreur récupération des Salary Slips : " + slipResponse.getStatusCode());
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            SalarySlipResponse slips = mapper.readValue(slipResponse.getBody(), SalarySlipResponse.class);

            for (SalarySlip slip : slips.getData()) {
                String slipName = slip.getName();

                String detailUrl = UriComponentsBuilder
                        .fromHttpUrl(BASE_URL + "/api/resource/Salary Slip/" + slipName)
                        .queryParam("sid", sid)
                        .build().toUriString();

                ResponseEntity<String> detailResponse = restTemplate.exchange(detailUrl, HttpMethod.GET, entity,
                        String.class);

                if (!detailResponse.getStatusCode().is2xxSuccessful()) {
                    System.err.println("❌ Échec récupération détails du slip " + slipName);
                    continue;
                }

                SalarySlip detailedSlip = mapper.readValue(detailResponse.getBody(), SalarySlipWrapper.class).getData();

                boolean hasMatchingEarning = detailedSlip.getEarnings() != null && detailedSlip.getEarnings().stream()
                        .anyMatch(e -> verifierConditionComposantEtMontant(componentName, condition, value,
                                e.getSalary_component(), e.getAmount()));

                boolean hasMatchingDeduction = detailedSlip.getDeductions() != null
                        && detailedSlip.getDeductions().stream()
                                .anyMatch(d -> verifierConditionComposantEtMontant(componentName, condition, value,
                                        d.getSalary_component(), d.getAmount()));

                if (!hasMatchingEarning && !hasMatchingDeduction) {
                    continue;
                }

                // Affichage des détails
                System.out.println("\n📄-----------------------------");
                System.out.println("Slip Name     : " + detailedSlip.getName());
                System.out.println("Employee      : " + detailedSlip.getEmployee());
                System.out.println("Company       : " + detailedSlip.getCompany());
                System.out.println("Structure     : " + detailedSlip.getSalary_structure());
                System.out.println("Start Date    : " + detailedSlip.getStart_date());
                System.out.println("End Date      : " + detailedSlip.getEnd_date());
                System.out.println("Posting Date  : " + detailedSlip.getPosting_date());
                System.out.println("Gross Pay     : " + detailedSlip.getGross_pay());
                System.out.println("Net Pay       : " + detailedSlip.getNet_pay());

                System.out.println("🟢 Earnings (filtrés) :");
                if (hasMatchingEarning) {
                    detailedSlip.getEarnings().stream()
                            .filter(e -> verifierConditionComposantEtMontant(componentName, condition, value,
                                    e.getSalary_component(), e.getAmount()))
                            .forEach(e -> System.out.printf("  - Component: %-20s Montant: %.2f%n",
                                    e.getSalary_component(), e.getAmount()));
                } else {
                    System.out.println("  (Aucun earning ne respecte la condition)");
                }

                System.out.println("🔴 Deductions (filtrées) :");
                if (hasMatchingDeduction) {
                    detailedSlip.getDeductions().stream()
                            .filter(d -> verifierConditionComposantEtMontant(componentName, condition, value,
                                    d.getSalary_component(), d.getAmount()))
                            .forEach(d -> System.out.printf("  - Component: %-20s Montant: %.2f%n",
                                    d.getSalary_component(), d.getAmount()));
                } else {
                    System.out.println("  (Aucune deduction ne respecte la condition)");
                }

                System.out.println("-----------------------------");

                // ✅ Annulation du Salary Slip
                try {
                    ObjectNode cancelPayload = mapper.createObjectNode();
                    cancelPayload.put("docstatus", 2);

                    HttpEntity<String> cancelEntity = new HttpEntity<>(cancelPayload.toString(), headers);
                    String cancelUrl = BASE_URL + "/api/resource/Salary Slip/" + detailedSlip.getName();

                    ResponseEntity<String> cancelResponse = restTemplate.exchange(
                            cancelUrl,
                            HttpMethod.PUT,
                            cancelEntity,
                            String.class);

                    if (cancelResponse.getStatusCode().is2xxSuccessful()) {
                        System.out.println("✅ Slip annulé : " + detailedSlip.getName());

                        // Maintenant, suppression du Salary Slip
                        ResponseEntity<String> deleteResponse = restTemplate.exchange(
                                cancelUrl,
                                HttpMethod.DELETE,
                                new HttpEntity<>(headers),
                                String.class);

                        if (deleteResponse.getStatusCode().is2xxSuccessful()) {
                            System.out.println("✅ Slip supprimé : " + detailedSlip.getName());
                        } else {
                            System.err.println("❌ Échec de la suppression du slip " + detailedSlip.getName() + " : "
                                    + deleteResponse.getStatusCode());
                        }

                    } else {
                        System.err.println("❌ Échec de l'annulation pour " + detailedSlip.getName() + " : "
                                + cancelResponse.getStatusCode());
                    }

                } catch (Exception ex) {
                    System.err.println(
                            "❌ Exception lors de l'annulation ou suppression du slip " + detailedSlip.getName() + " : "
                                    + ex.getMessage());
                    ex.printStackTrace();
                }

                // ✅ Annulation du Salary Structure Assignment associé
                try {
                    String employee = detailedSlip.getEmployee();
                    String salaryStructure = detailedSlip.getSalary_structure();
                    String fromDate = detailedSlip.getStart_date();
                    String endDate = detailedSlip.getEnd_date();

                    if (employee != null && salaryStructure != null && fromDate != null) {
                        System.out.println("📎 Tentative d'annulation de l'Assignment pour : "
                                + employee + " | " + salaryStructure + " | " + fromDate);

                        // Construire la requête pour retrouver l’Assignment exact
                        String assignmentQueryUrl = UriComponentsBuilder
                                .fromHttpUrl(BASE_URL + "/api/resource/Salary Structure Assignment")
                                .queryParam("filters", "[[\"employee\",\"=\",\"" + employee + "\"]," +
                                        "[\"salary_structure\",\"=\",\"" + salaryStructure + "\"]," +
                                        "[\"from_date\",\"=\",\"" + fromDate + "\"]]")
                                .queryParam("fields", "[\"name\", \"base\"]")
                                .build().toUriString();

                        ResponseEntity<String> assignmentQueryResponse = restTemplate.exchange(
                                assignmentQueryUrl,
                                HttpMethod.GET,
                                entity,
                                String.class);

                        if (assignmentQueryResponse.getStatusCode().is2xxSuccessful()) {
                            JsonNode assignmentRoot = mapper.readTree(assignmentQueryResponse.getBody());
                            JsonNode dataArray = assignmentRoot.path("data");

                            if (dataArray.isArray() && dataArray.size() > 0) {
                                String assignmentName = dataArray.get(0).get("name").asText();

                                double baseValue = dataArray.get(0).has("base")
                                        ? dataArray.get(0).get("base").asDouble()
                                        : 0.0;
                                System.out.println("💰 Valeur de base avant annulation : " + baseValue);

                                System.out.println(
                                        "🗂️  Assignment trouvé : " + assignmentName + " → tentative d'annulation...");

                                ObjectNode cancelAssignmentPayload = mapper.createObjectNode();
                                cancelAssignmentPayload.put("docstatus", 2);

                                HttpEntity<String> cancelAssignmentEntity = new HttpEntity<>(
                                        cancelAssignmentPayload.toString(), headers);
                                String cancelAssignmentUrl = BASE_URL + "/api/resource/Salary Structure Assignment/"
                                        + assignmentName;

                                ResponseEntity<String> cancelAssignmentResponse = restTemplate.exchange(
                                        cancelAssignmentUrl,
                                        HttpMethod.PUT,
                                        cancelAssignmentEntity,
                                        String.class);

                                if (cancelAssignmentResponse.getStatusCode().is2xxSuccessful()) {
                                    System.out.println("✅ Assignment annulé : " + assignmentName);

                                    ResponseEntity<String> deleteAssignmentResponse = restTemplate.exchange(
                                            cancelAssignmentUrl,
                                            HttpMethod.DELETE,
                                            new HttpEntity<>(headers),
                                            String.class);

                                    if (deleteAssignmentResponse.getStatusCode().is2xxSuccessful()) {
                                        System.out.println("✅ Assignment supprimé : " + assignmentName);
                                    } else {
                                        System.err.println(
                                                "❌ Échec de la suppression de l'Assignment " + assignmentName + " : "
                                                        + deleteAssignmentResponse.getStatusCode());
                                    }

                                    System.out.println("📐 condition = " + action + ", percentage = " + percentage);

                                    double nouveauMontant = calculerMontant(action, baseValue, percentage);

                                    ObjectNode newAssignmentPayload = mapper.createObjectNode();
                                    newAssignmentPayload.put("employee", employee);
                                    newAssignmentPayload.put("salary_structure", salaryStructure);
                                    newAssignmentPayload.put("from_date", fromDate);
                                    newAssignmentPayload.put("base", nouveauMontant);
                                    newAssignmentPayload.put("docstatus", 1); // Soumettre le document directement

                                    HttpEntity<String> createAssignmentEntity = new HttpEntity<>(
                                            newAssignmentPayload.toString(), headers);

                                    String createAssignmentUrl = BASE_URL + "/api/resource/Salary Structure Assignment";

                                    ResponseEntity<String> createAssignmentResponse = restTemplate.exchange(
                                            createAssignmentUrl,
                                            HttpMethod.POST,
                                            createAssignmentEntity,
                                            String.class);

                                    if (createAssignmentResponse.getStatusCode().is2xxSuccessful()) {
                                        System.out.println("✅ Assignment recréé pour " + employee
                                                + " avec montant ajusté : " + nouveauMontant);

                                        ObjectNode salarySlipPayload = mapper.createObjectNode();
                                        salarySlipPayload.put("employee", employee);
                                        salarySlipPayload.put("start_date", fromDate);
                                        salarySlipPayload.put("end_date", endDate); // ou une date de fin, à ajuster si
                                                                                    // nécessaire
                                        salarySlipPayload.put("salary_structure", salaryStructure);
                                        salarySlipPayload.put("docstatus", 1);

                                        HttpEntity<String> salarySlipEntity = new HttpEntity<>(
                                                salarySlipPayload.toString(), headers);

                                        String salarySlipUrl = BASE_URL + "/api/resource/Salary Slip";

                                        ResponseEntity<String> salarySlipResponse = restTemplate.exchange(
                                                salarySlipUrl,
                                                HttpMethod.POST,
                                                salarySlipEntity,
                                                String.class);

                                        if (salarySlipResponse.getStatusCode().is2xxSuccessful()) {
                                            System.out.println("✅ Salary Slip créé avec succès pour " + employee);
                                        } else {
                                            System.err.println("❌ Échec de création du Salary Slip : "
                                                    + salarySlipResponse.getStatusCode());
                                        }
                                    } else {
                                        System.err.println("❌ Échec de la création du nouvel Assignment : "
                                                + createAssignmentResponse.getStatusCode());
                                    }
                                } else {
                                    System.err
                                            .println("❌ Échec de l'annulation de l'Assignment " + assignmentName + " : "
                                                    + cancelAssignmentResponse.getStatusCode());
                                }

                            } else {
                                System.out.println("❌ Aucun Assignment correspondant trouvé.");
                            }
                        } else {
                            System.err.println("❌ Erreur de récupération de l'Assignment : "
                                    + assignmentQueryResponse.getStatusCode());
                        }
                    }

                } catch (Exception ex) {
                    System.err.println("❌ Exception lors de l'annulation de l'Assignment : " + ex.getMessage());
                    ex.printStackTrace();
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Exception lors de la récupération des Salary Slips : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean verifierCondition(double montant, String condition, double value) {
        return switch (condition) {
            case ">" -> montant > value;
            case "<" -> montant < value;
            case "=" -> montant == value;
            case ">=" -> montant >= value;
            case "<=" -> montant <= value;
            default -> false;
        };
    }

    // méthode modifiée pour filtrer un earning/deduction selon componentName ET
    // condition
    private boolean verifierConditionComposantEtMontant(String componentName, String condition, double value,
            String composantTest, double montantTest) {
        // On vérifie d'abord que le nom du composant correspond (ex: "Indemnité")
        if (!composantTest.equalsIgnoreCase(componentName)) {
            return false; // ce composant ne correspond pas au filtre sur le nom
        }
        // Puis on applique la condition sur le montant
        return verifierCondition(montantTest, condition, value);
    }

    public double calculerMontant(String condition, double baseInitiale, double pourcentage) {
        if ("ajouter".equalsIgnoreCase(condition)) {
            return baseInitiale + (baseInitiale * pourcentage / 100);
        } else if ("enlever".equalsIgnoreCase(condition)) {
            return baseInitiale - (baseInitiale * pourcentage / 100);
        } else {
            return baseInitiale; // Si la condition n'est pas reconnue
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SalaryAssignmentResponse {
        private List<SalaryAssignment> data;

        public List<SalaryAssignment> getData() {
            return data;
        }

        public void setData(List<SalaryAssignment> data) {
            this.data = data;
        }
    }

    // Classe interne pour mapper la réponse JSON
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SalarySlipResponse {
        private List<SalarySlip> data;

        public List<SalarySlip> getData() {
            return data;
        }

        public void setData(List<SalarySlip> data) {
            this.data = data;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SalarySlipWrapper {
        private SalarySlip data;

        public SalarySlip getData() {
            return data;
        }

        public void setData(SalarySlip data) {
            this.data = data;
        }
    }

    public List<SalarySlip> rechercherSalaireEtRetourner(String condition, double montant, String componentName,
            HttpSession session) {

        List<SalarySlip> resultats = new ArrayList<>();

        String sid = (String) session.getAttribute("sid");

        if (sid == null || sid.isEmpty()) {
            throw new RuntimeException("Session non authentifiée");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Cookie", "sid=" + sid);
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String slipUrl = UriComponentsBuilder
                    .fromHttpUrl(BASE_URL + "/api/resource/Salary Slip")
                    .queryParam("limit_page_length", "1000")
                    .queryParam("fields", "[\"*\"]")
                    .build().toUriString();

            ResponseEntity<String> slipResponse = restTemplate.exchange(slipUrl, HttpMethod.GET, entity, String.class);

            if (!slipResponse.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Erreur récupération Salary Slips");
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            SalarySlipResponse slips = mapper.readValue(slipResponse.getBody(), SalarySlipResponse.class);

            for (SalarySlip slip : slips.getData()) {

                String detailUrl = UriComponentsBuilder
                        .fromHttpUrl(BASE_URL + "/api/resource/Salary Slip/" + slip.getName())
                        .queryParam("sid", sid)
                        .build().toUriString();

                ResponseEntity<String> detailResponse = restTemplate.exchange(detailUrl, HttpMethod.GET, entity,
                        String.class);

                if (!detailResponse.getStatusCode().is2xxSuccessful()) {
                    continue;
                }

                SalarySlip detailedSlip = mapper.readValue(detailResponse.getBody(), SalarySlipWrapper.class).getData();

                boolean hasMatchingEarning = detailedSlip.getEarnings() != null && detailedSlip.getEarnings().stream()
                        .anyMatch(e -> verifierConditionComposantEtMontant(componentName, condition, montant,
                                e.getSalary_component(), e.getAmount()));

                boolean hasMatchingDeduction = detailedSlip.getDeductions() != null
                        && detailedSlip.getDeductions().stream()
                                .anyMatch(d -> verifierConditionComposantEtMontant(componentName, condition, montant,
                                        d.getSalary_component(), d.getAmount()));

                if (hasMatchingEarning || hasMatchingDeduction) {
                    resultats.add(detailedSlip);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Erreur récupération Salary Slips : " + e.getMessage());
        }

        return resultats;
    }

}