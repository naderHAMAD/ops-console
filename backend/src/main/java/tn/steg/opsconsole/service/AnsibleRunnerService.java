package tn.steg.opsconsole.service;

import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class AnsibleRunnerService {
	
    /** Exécute un playbook via WSL et retourne la sortie complète (stdout + stderr). */
   public String runPlaybook(String playbookName, java.util.Map<String, Object> extraVars) throws Exception {
        StringBuilder extraVarsArg = new StringBuilder();
        extraVars.forEach((k, v) -> extraVarsArg.append(k).append("=").append(v).append(" "));

        String command = String.format(
            "cd /mnt/c/Users/nader/Downloads/ops-console-skeleton/ansible && ansible-playbook playbooks/%s -e \"%s\"",
            playbookName, extraVarsArg.toString().trim()
        );

        ProcessBuilder pb = new ProcessBuilder("wsl.exe", "bash", "-c", command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        process.waitFor();
        return output.toString();
    }

 
    /**
     * Écrit un playbook et un fichier de variables reçus dynamiquement sur disque,
     * puis exécute ansible-playbook dessus via WSL. Utile pour tester un playbook
     * arbitraire choisi par l'utilisateur contre une spec VM donnée.
     */
   public String runUploadedPlaybook(String playbookContent, String varsJson) throws Exception {
       String timestamp = String.valueOf(System.currentTimeMillis());

       // Chemin absolu Windows vers la racine du projet (ajuste si ton dossier est différent)
       String projectRootWindows = "C:\\Users\\nader\\Downloads\\ops-console-skeleton";
       String projectRootWsl = "/mnt/c/Users/nader/Downloads/ops-console-skeleton";

       java.nio.file.Path playbookDir = java.nio.file.Paths.get(projectRootWindows, "ansible", "playbooks", "uploaded");
       java.nio.file.Files.createDirectories(playbookDir);
       java.nio.file.Path playbookFile = playbookDir.resolve("uploaded-" + timestamp + ".yml");
       java.nio.file.Files.writeString(playbookFile, playbookContent);

       java.nio.file.Path varsDir = java.nio.file.Paths.get(projectRootWindows, "ansible", "vars");
       java.nio.file.Files.createDirectories(varsDir);
       java.nio.file.Path varsFile = varsDir.resolve("vm-" + timestamp + ".json");
       java.nio.file.Files.writeString(varsFile, varsJson);

       String command = String.format(
           "cd %s/ansible && ansible-playbook playbooks/uploaded/uploaded-%s.yml -e @vars/vm-%s.json",
           projectRootWsl, timestamp, timestamp
       );

       ProcessBuilder pb = new ProcessBuilder("wsl.exe", "bash", "-c", command);
       pb.redirectErrorStream(true);
       Process process = pb.start();

       StringBuilder output = new StringBuilder();
       try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
           String line;
           while ((line = reader.readLine()) != null) {
               output.append(line).append("\n");
           }
       }
       process.waitFor();
       return output.toString();
   }
   /** Exécute un playbook avec un inventaire réel et des extra_vars, retourne la sortie complète. */
   public String runPlaybookWithInventory(String playbookName, String inventoryRelativePath, java.util.Map<String, Object> extraVars) throws Exception {
       String projectRootWsl = "/mnt/c/Users/nader/Downloads/ops-console-skeleton";

       StringBuilder extraVarsArg = new StringBuilder();
       extraVars.forEach((k, v) -> extraVarsArg.append(k).append("=").append(v).append(" "));

       String command = String.format(
           "cd %s/ansible && ansible-playbook -i %s playbooks/%s -e \"%s\"",
           projectRootWsl, inventoryRelativePath, playbookName, extraVarsArg.toString().trim()
       );

       ProcessBuilder pb = new ProcessBuilder("wsl.exe", "bash", "-c", command);
       pb.redirectErrorStream(true);
       Process process = pb.start();

       StringBuilder output = new StringBuilder();
       try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
           String line;
           while ((line = reader.readLine()) != null) {
               output.append(line).append("\n");
           }
       }
       process.waitFor();
       return output.toString();
   }
}