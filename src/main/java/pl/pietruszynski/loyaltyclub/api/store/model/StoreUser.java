package pl.pietruszynski.loyaltyclub.api.store.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import pl.pietruszynski.loyaltyclub.model.BaseUser;

@Entity
@Table(name = "store_users")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class StoreUser extends BaseUser {
}
