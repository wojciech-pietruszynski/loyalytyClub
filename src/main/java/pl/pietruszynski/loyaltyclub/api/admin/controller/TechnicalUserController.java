package pl.pietruszynski.loyaltyclub.api.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TechnicalUserCreateRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TechnicalUserDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TechnicalUserPasswordRequest;
import pl.pietruszynski.loyaltyclub.api.admin.audit.Auditable;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TechnicalUserStatusRequest;
import pl.pietruszynski.loyaltyclub.api.admin.model.TechnicalUser;
import pl.pietruszynski.loyaltyclub.api.admin.service.TechnicalUserService;
import pl.pietruszynski.loyaltyclub.api.admin.service.TechnicalUserService.TechnicalUserWithPassword;

import java.util.List;

@RestController
@RequestMapping("/api/admin/technical-users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TechnicalUserController {

    private final TechnicalUserService technicalUserService;

    @GetMapping
    public List<TechnicalUserDto> getTechnicalUsers() {
        return technicalUserService.getAllTechnicalUsers().stream()
                .map(this::mapToDto)
                .toList();
    }

    /** Odpowiedz zawiera haslo jednorazowe -- jedyny moment, w ktorym da sie je odczytac. */
    @PostMapping
    @Auditable(value = "CREATE_TECHNICAL_USER", resourceType = "TECHNICAL_USER")
    public TechnicalUserDto createTechnicalUser(@Valid @RequestBody TechnicalUserCreateRequest request) {
        return mapToDto(technicalUserService.createTechnicalUser(request));
    }

    @PatchMapping("/{id}/status")
    @Auditable(value = "SET_TECHNICAL_USER_STATUS", resourceType = "TECHNICAL_USER", capturePathId = true)
    public TechnicalUserDto setTechnicalUserStatus(@PathVariable Long id, @Valid @RequestBody TechnicalUserStatusRequest request) {
        return mapToDto(technicalUserService.setTechnicalUserEnabled(id, request.enabled()));
    }

    /** Ustawienie hasla wskazanego przez administratora; odpowiedz hasla nie zawiera. */
    @PatchMapping("/{id}/password")
    @Auditable(value = "UPDATE_TECHNICAL_USER_PASSWORD", resourceType = "TECHNICAL_USER", capturePathId = true)
    public TechnicalUserDto updateTechnicalUserPassword(@PathVariable Long id, @Valid @RequestBody TechnicalUserPasswordRequest request) {
        return mapToDto(technicalUserService.updateTechnicalUserPassword(id, request.password()));
    }

    /** Reset hasla: serwer generuje nowe haslo jednorazowe i zwraca je raz. */
    @PostMapping("/{id}/password-reset")
    @Auditable(value = "RESET_TECHNICAL_USER_PASSWORD", resourceType = "TECHNICAL_USER", capturePathId = true)
    public TechnicalUserDto resetTechnicalUserPassword(@PathVariable Long id) {
        return mapToDto(technicalUserService.resetTechnicalUserPassword(id));
    }

    private TechnicalUserDto mapToDto(TechnicalUserWithPassword result) {
        return toDtoBuilder(result.user())
                .oneTimePassword(result.oneTimePassword())
                .build();
    }

    private TechnicalUserDto mapToDto(TechnicalUser user) {
        return toDtoBuilder(user).build();
    }

    private TechnicalUserDto.TechnicalUserDtoBuilder toDtoBuilder(TechnicalUser user) {
        return TechnicalUserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .country(user.getCountry())
                .enabled(user.isEnabled())
                .passwordChangedAt(user.getPasswordChangedAt());
    }
}
