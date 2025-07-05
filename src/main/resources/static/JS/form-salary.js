document.addEventListener("DOMContentLoaded", function () {

    const employeeSelect = document.getElementById("ref");
    const companySelect = document.getElementById("company");
    const salaryStructureSelect = document.getElementById("salaryStructure");
    const montantInput = document.getElementById("montant");
    const useMoyenneCheckbox = document.getElementById("useMoyenne");

    employeeSelect.addEventListener("change", function () {
        const employeeId = this.value;

        // Réinitialise les champs
        companySelect.value = "";
        salaryStructureSelect.value = "";

        if (employeeId) {
            fetch(`/employee/info/${employeeId}`)
                .then(response => {
                    if (!response.ok) {
                        throw new Error("Erreur lors de la récupération des informations.");
                    }
                    return response.json();
                })
                .then(data => {
                    if (data) {
                        if (data.company) {
                            companySelect.value = data.company;
                        }
                        if (data.salary_structure) {
                            salaryStructureSelect.value = data.salary_structure;
                        }
                    } else {
                        console.warn("Aucune information trouvée pour cet employé.");
                    }
                })
                .catch(error => {
                    console.error("❌ Erreur :", error);
                });
        }
    });

    // Gérer la checkbox pour la moyenne des salaires
    useMoyenneCheckbox.addEventListener("change", function () {
        if (this.checked) {
            // Désactiver le champ montant et récupérer la moyenne
            montantInput.disabled = true;
            montantInput.value = "Calcul en cours...";
            
            // 🔧 Ajouter un appel de débogage d'abord
            fetch('/employee/debug-salaries')
                .then(response => response.json())
                .then(debugData => {
                    console.log("🔍 Données de débogage:", debugData);
                })
                .catch(error => console.warn("Débogage échoué:", error));
            
            // Puis l'appel normal
            fetch('/employee/average-salary')
                .then(response => {
                    if (!response.ok) {
                        throw new Error("Erreur lors de la récupération de la moyenne.");
                    }
                    return response.json();
                })
                .then(data => {
                    console.log("📊 Réponse API moyenne:", data);
                    if (data && data.average !== undefined) {
                        montantInput.value = data.average.toFixed(2);
                        console.log(`✅ Moyenne des salaires calculée: ${data.average.toFixed(2)}`);
                    } else {
                        montantInput.value = "0";
                        console.warn("Aucune moyenne trouvée.");
                    }
                })
                .catch(error => {
                    console.error("❌ Erreur lors du calcul de la moyenne:", error);
                    montantInput.value = "0";
                    alert("Erreur lors du calcul de la moyenne des salaires.");
                });
        } else {
            // Réactiver le champ montant
            montantInput.disabled = false;
            montantInput.value = "0";
        }
    });

});
