package pl.pietruszynski.loyaltyclub.api.admin.audit;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Zapisuje w logu audytowym udane wywolania endpointow oznaczonych {@link Auditable}.
 * Nieudane wywolania nie sa logowane — nie zmieniaja stanu systemu.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private static final String PATH_ID_PARAMETER = "id";

    private final AdminAuditLogRepository adminAuditLogRepository;

    @AfterReturning("@annotation(auditable)")
    public void recordSuccessfulAction(JoinPoint joinPoint, Auditable auditable) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        adminAuditLogRepository.save(AdminAuditLog.builder()
                .username(resolveUsername(authentication))
                .role(resolveRole(authentication))
                .action(auditable.value())
                .resourceType(auditable.resourceType())
                .resourceId(auditable.capturePathId() ? resolvePathId(joinPoint) : null)
                .build());
    }

    private String resolveUsername(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return "anonymous";
        }
        return authentication.getName();
    }

    private String resolveRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return null;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse(null);
    }

    private String resolvePathId(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        if (parameterNames == null) {
            return null;
        }
        for (int i = 0; i < parameterNames.length; i++) {
            if (PATH_ID_PARAMETER.equals(parameterNames[i]) && args[i] != null) {
                return String.valueOf(args[i]);
            }
        }
        return null;
    }
}
