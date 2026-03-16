package pl.pietruszynski.loyaltyclub.api.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TechnicalUserCreateRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TechnicalUserDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TechnicalUserPasswordRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TechnicalUserStatusRequest;
import pl.pietruszynski.loyaltyclub.api.admin.model.TechnicalUser;
import pl.pietruszynski.loyaltyclub.api.admin.service.TechnicalUserService;

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

    @PostMapping
    public TechnicalUserDto createTechnicalUser(@Valid @RequestBody TechnicalUserCreateRequest request) {
        return mapToDto(technicalUserService.createTechnicalUser(request));
    }

    @PatchMapping("/{id}/status")
    public TechnicalUserDto setTechnicalUserStatus(@PathVariable Long id, @Valid @RequestBody TechnicalUserStatusRequest request) {
        return mapToDto(technicalUserService.setTechnicalUserEnabled(id, request.enabled()));
    }

    @PatchMapping("/{id}/password")
    public TechnicalUserDto updateTechnicalUserPassword(@PathVariable Long id, @Valid @RequestBody TechnicalUserPasswordRequest request) {
        return mapToDto(technicalUserService.updateTechnicalUserPassword(id, request.password()));
    }

    private TechnicalUserDto mapToDto(TechnicalUser user) {
        return TechnicalUserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .passwordPreview(user.getPasswordPreview())
                .country(user.getCountry())
                .enabled(user.isEnabled())
                .build();
    }
}
