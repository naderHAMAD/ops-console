package tn.steg.opsconsole.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tn.steg.opsconsole.domain.Server;
import tn.steg.opsconsole.domain.UpdateJob;
import tn.steg.opsconsole.domain.User;
import tn.steg.opsconsole.dto.request.TriggerUpdateRequest;
import tn.steg.opsconsole.dto.response.UpdateResultDto;
import tn.steg.opsconsole.repository.ServerRepository;
import tn.steg.opsconsole.repository.UpdateJobRepository;
import tn.steg.opsconsole.repository.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UpdateJobService {

    private final UpdateJobRepository updateJobRepository;
    private final ServerRepository serverRepository;
    private final UserRepository userRepository;
    private final AnsibleRunnerService ansibleRunnerService;
    private final AuditService auditService;

    /** Inventaire de test pointant sur le conteneur jboss-test-nord01 (SSH port 2222). */
    private static final String TEST_INVENTORY = "inventories/test/hosts.yml";

    public List<UpdateResultDto> triggerUpdate(TriggerUpdateRequest request, Authentication authentication) {
        User currentUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Utilisateur courant introuvable"));

        List<UpdateResultDto> results = new ArrayList<>();

        for (var serverId : request.serverIds()) {
            Server server = serverRepository.findById(serverId)
                    .orElseThrow(() -> new IllegalArgumentException("Serveur introuvable: " + serverId));

            UpdateJob job = UpdateJob.builder()
                    .server(server)
                    .triggeredBy(currentUser)
                    .targetVersion(request.targetVersion())
                    .status("running")
                    .startedAt(Instant.now())
                    .build();
            job = updateJobRepository.save(job);

            String logs;
            String status;
            try {
                logs = ansibleRunnerService.runPlaybookWithInventory(
                        "update_jboss.yml",
                        TEST_INVENTORY,
                        Map.of(
                                "target_hostname", server.getHostname(),
                                "target_version", request.targetVersion()
                        )
                );
                // Le playbook fait échouer explicitement le job Ansible (rc != 0) en cas de
                // rollback — on détecte ça via la présence du message de rollback dans les logs.
                status = logs.contains("Rollback effectué avec succès") ? "rolled_back"
                        : logs.contains("PLAY RECAP") && logs.contains("failed=0") ? "success"
                        : "failed";
            } catch (Exception e) {
                logs = "Erreur d'exécution : " + e.getMessage();
                status = "failed";
            }

            job.setStatus(status);
            job.setLogs(logs);
            job.setFinishedAt(Instant.now());
            updateJobRepository.save(job);

            auditService.log(currentUser, "UPDATE_TRIGGERED", "Server", server.getId(),
                    "Version " + request.targetVersion() + " — statut: " + status);

            results.add(new UpdateResultDto(job.getId(), server.getHostname(), status, logs));
        }

        return results;
    }
}