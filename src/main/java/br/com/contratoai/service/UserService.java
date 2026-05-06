package br.com.contratoai.service;

import br.com.contratoai.domain.entity.User;
import br.com.contratoai.domain.enums.AuditAction;
import br.com.contratoai.domain.enums.Plan;
import br.com.contratoai.exception.UserNotFoundException;
import br.com.contratoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    // Limite de documentos por mês no plano gratuito
    private static final int FREE_PLAN_MONTHLY_LIMIT = 3;

    /**
     * Retorna o usuário local correspondente ao JWT do Keycloak.
     * Se o usuário não existir no banco, cria automaticamente (provisionamento lazy).
     */
    @Transactional
    public User getOrCreateUser(Jwt jwt) {
        String keycloakId = jwt.getSubject();

        return userRepository.findByKeycloakId(keycloakId)
            .orElseGet(() -> createUserFromJwt(jwt));
    }

    @Transactional(readOnly = true)
    public User findById(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("Usuário não encontrado: " + id));
    }

    public boolean canCreateDocument(User user, long documentsThisMonth) {
        if (user.getPlan() == Plan.FREE) {
            return documentsThisMonth < FREE_PLAN_MONTHLY_LIMIT;
        }
        return true; // PRO e BUSINESS são ilimitados
    }

    private User createUserFromJwt(Jwt jwt) {
        User newUser = User.builder()
            .keycloakId(jwt.getSubject())
            .email(jwt.getClaimAsString("email"))
            .name(jwt.getClaimAsString("name"))
            .plan(Plan.FREE)
            .build();

        User saved = userRepository.save(newUser);

        auditService.logUserAction(AuditAction.USER_CREATED, saved.getId(),
            Map.of("email", saved.getEmail(), "plan", saved.getPlan().name()));
        log.info("Novo usuário provisionado: id={}, email={}", saved.getId(), saved.getEmail());

        return saved;
    }
}
