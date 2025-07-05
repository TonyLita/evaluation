package com.spring.erpnext.controller;

import com.spring.erpnext.model.Company;
import com.spring.erpnext.model.Employee;
import com.spring.erpnext.service.BaseSalaryService;
import com.spring.erpnext.service.CompanyService;
import com.spring.erpnext.service.EmployeeService;
import com.spring.erpnext.service.TestService;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class EmployeeController {

    private final EmployeeService employeeService;
    private final CompanyService companyService;
    private final TestService testService;
    private final BaseSalaryService baseSalaryService;
    private final RestTemplate restTemplate;

    @Autowired
    public EmployeeController(EmployeeService employeeService, CompanyService companyService, TestService testService,
            BaseSalaryService baseSalaryService, RestTemplate restTemplate) {
        this.employeeService = employeeService;
        this.companyService = companyService;
        this.testService = testService;
        this.baseSalaryService = baseSalaryService;
        this.restTemplate = restTemplate;
    }

    @GetMapping("/employees")
    public String showEmployeesPage(
            HttpSession session,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String search,
            Model model) {

        List<Employee> employees = employeeService.getAllEmployees(session);

        if (employees == null) {
            return "redirect:/login";
        }

        if (search != null && !search.isEmpty()) {
            String lowerSearch = search.toLowerCase();
            employees = employees.stream()
                    .filter(e -> (e.getLast_name() != null && e.getLast_name().toLowerCase().contains(lowerSearch)) ||
                            (e.getFirst_name() != null && e.getFirst_name().toLowerCase().contains(lowerSearch)) ||
                            (e.getMiddle_name() != null && e.getMiddle_name().toLowerCase().contains(lowerSearch)))
                    .toList();
        }

        int pageSize = 10;
        int totalPages = (int) Math.ceil((double) employees.size() / pageSize);
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, employees.size());

        List<Employee> paginated = employees.subList(fromIndex, toIndex);
        String username = (String) session.getAttribute("username");

        model.addAttribute("username", username);
        model.addAttribute("employees", paginated);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("search", search);
        model.addAttribute("page", "employees");

        return "layout/base";
    }

    @GetMapping("/employees/delete/{name}")
    public String deleteEmployee(
            @PathVariable("name") String name,
            HttpSession session) {

        employeeService.deleteEmployee(name, session);

        return "redirect:/employees";
    }

    @GetMapping("/employees-add")
    public String InsertEmployePage(HttpSession session, Model model) {
        if (session.getAttribute("sid") == null) {
            return "redirect:/login";
        }

        List<Company> company = companyService.getAllCompanies(session);

        String username = (String) session.getAttribute("username");
        model.addAttribute("username", username);
        model.addAttribute("company", company);
        model.addAttribute("page", "employees-add");

        model.addAttribute("employee", new Employee());

        return "layout/base";
    }

    @PostMapping("/employees-add")
    public String submitEmployeeForm(
            @RequestParam("name") String name,
            @RequestParam("first_name") String firstName,
            @RequestParam(value = "middle_name", required = false) String middleName,
            @RequestParam("last_name") String lastName,
            @RequestParam("date_of_birth") String dateOfBirth,
            @RequestParam("date_of_joining") String dateOfJoining,
            @RequestParam("gender") String gender,
            @RequestParam("company") String company,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Employee employee = new Employee();
        employee.setName(name);
        employee.setFirst_name(firstName);
        employee.setMiddle_name(middleName);
        employee.setLast_name(lastName);
        employee.setDate_of_birth(dateOfBirth);
        employee.setDate_of_joining(dateOfJoining);
        employee.setGender(gender);
        employee.setCompany(company);

        boolean success = employeeService.insertEmployee(employee, session);

        if (success) {
            redirectAttributes.addFlashAttribute("message", "✅ Employé ajouté avec succès.");
        } else {
            redirectAttributes.addFlashAttribute("error", "❌ Échec de l'ajout de l'employé.");
        }

        return "redirect:/employees-add";
    }

    @GetMapping("/employex")
    public ResponseEntity<List<Employee>> getAllEmployees() {
        List<Employee> employees = testService.getAllEmployees();
        return ResponseEntity.ok(employees);
    }

    @GetMapping("/employee/info/{employeeId}")
    @ResponseBody
    public Map<String, String> getSalaryStructureAndCompany(@PathVariable String employeeId, HttpSession session) {
        Map<String, String> infos = baseSalaryService.getSalaryStructureAndCompany(session, employeeId);

        if (infos != null) {
            return infos;
        } else {
            // Retourne une map vide ou un message d'erreur, selon ta préférence
            return Collections.emptyMap();
        }
    }

    @GetMapping("/employee/average-salary")
    @ResponseBody
    public Map<String, Double> getAverageBaseSalary(HttpSession session) {
        double average = baseSalaryService.getAverageBaseSalary(session);
        return Collections.singletonMap("average", average);
    }

    @GetMapping("/employeeBD")
    public String getEmployeesPage(Model model) {
        List<Employee> employees = testService.getAllEmployees();
        model.addAttribute("employees", employees);
        model.addAttribute("page", "employeeBD");
        return "layout/base";
    }

    @GetMapping("/employee/debug-salaries")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> debugSalaries(HttpSession session) {
        String sid = (String) session.getAttribute("sid");
        if (sid == null || sid.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // Appel direct à l'API Frappe pour voir les données brutes
            String url = "http://erpnext.localhost:8003/api/resource/Salary Structure Assignment?filters="
                    + "[[\"Salary Structure Assignment\",\"docstatus\",\"=\",\"1\"]]"
                    + "&fields=[\"base\",\"employee\",\"from_date\",\"creation\"]"
                    + "&limit_page_length=1000"
                    + "&order_by=creation desc";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Cookie", "sid=" + sid);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            Map<String, Object> debugInfo = new HashMap<>();
            debugInfo.put("status", response.getStatusCode().value());
            debugInfo.put("rawResponse", response.getBody());
            debugInfo.put("calculatedAverage", baseSalaryService.getAverageBaseSalary(session));
            
            return ResponseEntity.ok(debugInfo);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
