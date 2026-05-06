package br.com.contratoai.service;

import br.com.contratoai.domain.entity.User;
import br.com.contratoai.domain.enums.Plan;
import br.com.contratoai.exception.UserNotFoundException;
import br.com.contratoai.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserService userService;

    private Jwt buildJwt(String sub, String email, String name) {
        return Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .subject(sub)
            .claim("email", email)
            .claim("name", name)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    }

    @Test
    @DisplayName("getOrCreateUser - should return existing user when found by keycloakId")
    void getOrCreateUser_existingUser() {
        Jwt jwt = buildJwt("kc-123", "victor@email.com", "Victor");
        User existingUser = User.builder()
            .id(UUID.randomUUID())
            .keycloakId("kc-123")
            .email("victor@email.com")
            .name("Victor")
            .plan(Plan.FREE)
            .build();

        when(userRepository.findByKeycloakId("kc-123")).thenReturn(Optional.of(existingUser));

        User result = userService.getOrCreateUser(jwt);

        assertThat(result).isEqualTo(existingUser);
        assertThat(result.getKeycloakId()).isEqualTo("kc-123");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("getOrCreateUser - should create new user when not found")
    void getOrCreateUser_newUser() {
        Jwt jwt = buildJwt("kc-new", "novo@email.com", "Novo Usuario");

        when(userRepository.findByKeycloakId("kc-new")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        User result = userService.getOrCreateUser(jwt);

        assertThat(result.getKeycloakId()).isEqualTo("kc-new");
        assertThat(result.getEmail()).isEqualTo("novo@email.com");
        assertThat(result.getName()).isEqualTo("Novo Usuario");
        assertThat(result.getPlan()).isEqualTo(Plan.FREE);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("canCreateDocument - FREE plan under limit should return true")
    void canCreateDocument_freeUnderLimit() {
        User user = User.builder().plan(Plan.FREE).build();

        assertThat(userService.canCreateDocument(user, 0)).isTrue();
        assertThat(userService.canCreateDocument(user, 1)).isTrue();
        assertThat(userService.canCreateDocument(user, 2)).isTrue();
    }

    @Test
    @DisplayName("canCreateDocument - FREE plan at limit should return false")
    void canCreateDocument_freeAtLimit() {
        User user = User.builder().plan(Plan.FREE).build();

        assertThat(userService.canCreateDocument(user, 3)).isFalse();
        assertThat(userService.canCreateDocument(user, 5)).isFalse();
    }

    @Test
    @DisplayName("canCreateDocument - PRO plan should always return true")
    void canCreateDocument_proPlan() {
        User user = User.builder().plan(Plan.PRO).build();

        assertThat(userService.canCreateDocument(user, 0)).isTrue();
        assertThat(userService.canCreateDocument(user, 100)).isTrue();
    }

    @Test
    @DisplayName("canCreateDocument - BUSINESS plan should always return true")
    void canCreateDocument_businessPlan() {
        User user = User.builder().plan(Plan.BUSINESS).build();

        assertThat(userService.canCreateDocument(user, 50)).isTrue();
    }

    @Test
    @DisplayName("findById - should return user when found")
    void findById_found() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).name("Test").build();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        User result = userService.findById(id);

        assertThat(result.getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("findById - should throw when not found")
    void findById_notFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(id))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessageContaining("Usuário não encontrado");
    }
}
