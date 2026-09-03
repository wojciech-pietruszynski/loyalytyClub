package pl.pietruszynski.loyaltyclub.api.admin.audit;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Aspekt logu audytowego.
 *
 * <p>Log audytowy jest zapisem tego, kto zmienil stan programu -- przy sporze
 * o naliczenie punktow albo wydanie kuponu jest jedynym dowodem. Zapisujemy
 * wylacznie wywolania zakonczone powodzeniem: proba, ktora nic nie zmienila,
 * nie jest zdarzeniem wartym rejestracji.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditAspectTest {

    @Mock private AdminAuditLogRepository adminAuditLogRepository;
    @Mock private JoinPoint joinPoint;
    @Mock private MethodSignature signature;

    @InjectMocks
    private AuditAspect auditAspect;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordSuccessfulAction_shouldSaveActionWithAuthenticatedUserAndRole() {
        authenticateAs("admin", "ROLE_ADMIN");
        givenParameters(new String[]{}, new Object[]{});

        auditAspect.recordSuccessfulAction(joinPoint, auditable("CREATE_CUSTOMER", "CUSTOMER", false));

        AdminAuditLog log = savedLog();
        assertThat(log.getUsername()).isEqualTo("admin");
        assertThat(log.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(log.getAction()).isEqualTo("CREATE_CUSTOMER");
        assertThat(log.getResourceType()).isEqualTo("CUSTOMER");
        assertThat(log.getResourceId()).isNull();
    }

    /** Identyfikator zasobu bierzemy z parametru sciezki o nazwie {@code id}. */
    @Test
    void recordSuccessfulAction_shouldCapturePathIdWhenRequested() {
        authenticateAs("admin", "ROLE_ADMIN");
        givenParameters(new String[]{"id", "request"}, new Object[]{42L, "cokolwiek"});

        auditAspect.recordSuccessfulAction(joinPoint, auditable("ADD_POINTS", "CUSTOMER", true));

        assertThat(savedLog().getResourceId()).isEqualTo("42");
    }

    @Test
    void recordSuccessfulAction_shouldNotCapturePathIdWhenNotRequested() {
        authenticateAs("admin", "ROLE_ADMIN");
        givenParameters(new String[]{"id"}, new Object[]{42L});

        auditAspect.recordSuccessfulAction(joinPoint, auditable("ADD_POINTS", "CUSTOMER", false));

        assertThat(savedLog().getResourceId()).isNull();
    }

    /** Metoda bez parametru {@code id} nie moze wywrocic zapisu do logu. */
    @Test
    void recordSuccessfulAction_withoutIdParameter_shouldLeaveResourceIdEmpty() {
        authenticateAs("admin", "ROLE_ADMIN");
        givenParameters(new String[]{"request"}, new Object[]{"cokolwiek"});

        auditAspect.recordSuccessfulAction(joinPoint, auditable("CREATE_CUSTOMER", "CUSTOMER", true));

        assertThat(savedLog().getResourceId()).isNull();
    }

    @Test
    void recordSuccessfulAction_withNullIdArgument_shouldLeaveResourceIdEmpty() {
        authenticateAs("admin", "ROLE_ADMIN");
        givenParameters(new String[]{"id"}, new Object[]{null});

        auditAspect.recordSuccessfulAction(joinPoint, auditable("ADD_POINTS", "CUSTOMER", true));

        assertThat(savedLog().getResourceId()).isNull();
    }

    /**
     * Brak kontekstu uwierzytelnienia nie moze przerwac zapisu -- wpis bez autora
     * jest mniej wart, ale luka w logu audytowym jest gorsza niz wpis "anonymous".
     */
    @Test
    void recordSuccessfulAction_withoutAuthentication_shouldStillWriteAnEntry() {
        SecurityContextHolder.clearContext();
        givenParameters(new String[]{}, new Object[]{});

        auditAspect.recordSuccessfulAction(joinPoint, auditable("CREATE_CUSTOMER", "CUSTOMER", false));

        AdminAuditLog log = savedLog();
        assertThat(log.getUsername()).isEqualTo("anonymous");
        assertThat(log.getRole()).isNull();
    }

    @Test
    void recordSuccessfulAction_shouldRecordTechnicalUserRole() {
        authenticateAs("integracja", "ROLE_TECHNICAL");
        givenParameters(new String[]{}, new Object[]{});

        auditAspect.recordSuccessfulAction(joinPoint, auditable("EXPORT_CUSTOMERS", "REPORT", false));

        assertThat(savedLog().getRole()).isEqualTo("ROLE_TECHNICAL");
    }

    private void authenticateAs(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a",
                        List.of(new SimpleGrantedAuthority(role))));
    }

    private void givenParameters(String[] names, Object[] args) {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(names);
        when(joinPoint.getArgs()).thenReturn(args);
    }

    private AdminAuditLog savedLog() {
        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    private Auditable auditable(String action, String resourceType, boolean capturePathId) {
        return new Auditable() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Auditable.class;
            }

            @Override
            public String value() {
                return action;
            }

            @Override
            public String resourceType() {
                return resourceType;
            }

            @Override
            public boolean capturePathId() {
                return capturePathId;
            }
        };
    }
}
