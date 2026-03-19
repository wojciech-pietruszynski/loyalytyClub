package pl.pietruszynski.loyaltyclub.api.ecom.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import pl.pietruszynski.loyaltyclub.model.BaseUser;

@Entity
@Table(name = "ecom_users")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class EcomUser extends BaseUser {
}
