package pl.pietruszynski.loyaltyclub.api.admin.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import pl.pietruszynski.loyaltyclub.model.BaseUser;

@Entity
@Table(name = "admin_users")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AdminUser extends BaseUser {
}
