# Backend Rules – Loyalty Club

## 1. Struktura pakietów

```
pl.pietruszynski.loyaltyclub
├── config/              ← globalna konfiguracja Spring (SecurityConfig)
├── exception/           ← BusinessException, ResourceNotFoundException, GlobalExceptionHandler
├── model/               ← współdzielone klasy bazowe (BaseUser)
├── util/                ← narzędzia wielokrotnego użytku (CouponCodeGenerator)
└── api/
    ├── admin/           ← panel administracyjny (controller, service, repository, model, dto, security, config)
    ├── store/           ← transakcje sklepowe
    ├── ecom/            ← integracja e-commerce
    └── coupon/          ← realizacja kuponów
```

**Zasady:**
- Każda poddomena ma własne podpakiety: `controller`, `service`, `repository`, `model`, `dto`, `security`, `config`
- Klasy współdzielone między domenami trafiają do pakietów top-level (`model/`, `util/`, `exception/`)
- Nazwy pakietów: `lowercase` bez myślników
- Nazwy klas: `PascalCase`
- Nazwy metod i pól: `camelCase`
- Stałe: `UPPER_SNAKE_CASE`

---

## 2. Encje JPA / modele

**Szablon encji:**
```java
@Entity
@Table(name = "table_name")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "my_entity_seq")
    @SequenceGenerator(name = "my_entity_seq", sequenceName = "my_entity_id_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
```

**Zasady:**
- Zawsze `@NoArgsConstructor` (wymagane przez JPA) + `@AllArgsConstructor` + `@Builder`
- Klucz główny: `GenerationType.SEQUENCE` z `allocationSize = 1` (precyzyjna kontrola)
- `GenerationType.IDENTITY` tylko dla prostych tabel (bez sekwencji w Liquibase)
- Pola enum: `@Enumerated(EnumType.STRING)` – nigdy `ORDINAL`
- Relacje leniwe: `@ManyToOne(fetch = FetchType.LAZY)`
- Kaskady: `@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)`
- Wartości domyślne w builderze: `@Builder.Default`
- Logika inicjalizacyjna pól: `@PrePersist`
- Encje współdzielące pola `id, username, password, enabled` dziedziczą z `BaseUser` (`@MappedSuperclass` + `@SuperBuilder`)

---

## 3. Warstwa serwisów

**Szablon serwisu:**
```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)   // domyślnie tylko odczyt
public class MyService {

    private final MyRepository myRepository;

    @Value("${app.some-config:defaultValue}")
    private String someConfig;

    public List<MyEntity> getAll() { ... }      // dziedziczy readOnly = true

    @Transactional                               // nadpisuje readOnly dla zapisu
    public MyEntity create(MyEntity entity) { ... }
}
```

**Zasady:**
- Klasa zawsze `@Transactional(readOnly = true)` — metody zapisu nadpisują `@Transactional`
- Wstrzykiwanie zależności wyłącznie przez konstruktor (`@RequiredArgsConstructor`) — nigdy `@Autowired` na polu
- Konfiguracja: `@Value("${klucz:domyślna}")` — zawsze z wartością domyślną
- Walidacja biznesowa: rzucaj `BusinessException` (nie `RuntimeException`, `IllegalStateException` itp.)
- Zasób nie znaleziony: rzucaj `ResourceNotFoundException`
- Nie zwracaj `null` — używaj `Optional` lub rzuć wyjątek
- Metody prywatne pomocnicze: wydzielaj logikę (np. `normalizeCountryCode`, `isBlank`)

---

## 4. Wyjątki

**Hierarchia:**
```
RuntimeException
├── BusinessException          ← błąd logiki biznesowej (400)
└── ResourceNotFoundException  ← zasób nie istnieje (404)
```

**Zasady:**
- `BusinessException` — walidacja domenowa, reguły biznesowe, błędy importu
- `ResourceNotFoundException` — gdy `findById` / `findByX` nie zwróci wyniku
- Nigdy nie rzucaj gołego `RuntimeException` ani `IllegalArgumentException` w serwisach
- Stałe dla wielokrotnie używanych komunikatów:
  ```java
  private static final String COUNTRY_NOT_ALLOWED = "Country code is not allowed";
  ```
- `GlobalExceptionHandler` mapuje wyjątki na `ProblemDetail` (RFC 7807):
  - `AuthenticationException` → 401
  - `AccessDeniedException` → 403
  - `ResourceNotFoundException` → 404
  - `BusinessException` / `RuntimeException` → 400
  - `MethodArgumentNotValidException` → 400 z mapą `errors`

---

## 5. Kontrolery REST

**Szablon kontrolera:**
```java
@RestController
@RequestMapping("/api/domain")
@RequiredArgsConstructor
public class MyController {

    private final MyService myService;

    @GetMapping
    public List<MyDto> getAll(Authentication authentication) { ... }

    @PostMapping
    public MyDto create(@Valid @RequestBody MyDto dto) { ... }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL')")
    public MyDto updateStatus(@PathVariable Long id, @RequestBody StatusRequest req) { ... }
}
```

**Zasady:**
- Mapowanie w kontrolerze (nie w serwisie) — serwis operuje na encjach, kontroler zwraca DTO
- `@Valid` na każdym `@RequestBody` z adnotacjami walidacyjnymi
- `Authentication authentication` — Spring wstrzykuje automatycznie; nie wyciągaj z `SecurityContextHolder`
- Autoryzacja na poziomie metody: `@PreAuthorize("hasAnyRole(...)")`
- Odpowiedzi: bezpośredni typ (`List<DTO>`, `DTO`) lub `ResponseEntity<T>` gdy potrzebny kod HTTP
- Nie wstawiaj logiki biznesowej do kontrolerów — tylko mapowanie i wywołanie serwisu
- Użyj `.toList()` (Java 16+) zamiast `.collect(Collectors.toList())`

---

## 6. DTO

**Record (preferowany dla żądań/odpowiedzi read-only):**
```java
public record LoginRequest(
    @NotBlank(message = "Username is required") String username,
    @NotBlank(message = "Password is required") String password
) {}
```

**Klasa z Lombok (gdy potrzebny builder lub mutowalność):**
```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CustomerDto {
    private Long id;
    @NotBlank private String firstName;
    @Email    private String email;
}
```

**Zasady:**
- Requestsy proste (tylko pola do odczytu): `record`
- Responsesy z builderem lub złożone: klasa Lombok z `@Builder`
- Adnotacje walidacyjne: `@NotBlank`, `@NotNull`, `@Email`, `@Positive`, `@PositiveOrZero`, `@Size`, `@DecimalMin`
- Walidacja zagnieżdżona: `List<@Valid @NotNull ItemRequest>`

---

## 7. Repozytoria

**Zasady:**
- Rozszerzaj `JpaRepository<Entity, Long>`
- Nazwy metod: Spring Data (`findByX`, `existsByX`, `findAllByXOrderByYAsc`)
- Złożone zapytania: `@Query` z JPQL (nie natywny SQL)
- Blokowanie pesymistyczne dla operacji concurrent: `@Lock(LockModeType.PESSIMISTIC_WRITE)`
- `Optional<T>` dla wyników nullable

---

## 8. Bezpieczeństwo i JWT

**Konfiguracja:**
- `SecurityConfig` w `config/` — filter chain, auth providers, password encoder
- JWT: HMAC-SHA512, biblioteka JJWT 0.12.6, expiracja 900 000 ms (15 min)
- Sekret JWT: zmienna środowiskowa `JWT_SECRET` (Base64-encoded, min. 64 bajty)
- Sesja: `STATELESS` — brak HTTP session

**Role:**
| Rola | Endpoint |
|------|----------|
| `ROLE_ADMIN` | `/api/admin/**` |
| `ROLE_TECHNICAL` | `/api/admin/**` (ograniczony scope kraju) |
| `ROLE_STORE` | `/api/store/**` |
| `ROLE_ECOM` | `/api/ecom/**`, `/api/coupon/**` |

**Zasady:**
- `JwtAuthFilter extends OncePerRequestFilter` — nie zatrzymuje requestu przy błędzie tokenu, tylko pomija ustawienie kontekstu
- Każda domena ma własny `UserDetailsService` (Admin, Store, Ecom)
- `PasswordEncoder`: `BCryptPasswordEncoder` — zawsze koduj hasła, nigdy nie przechowuj plaintext (wyjątek: `passwordPreview` dla adminów)
- Dla kryptograficznego generowania losowych danych: `SecureRandom` (nie `Random`, nie `ThreadLocalRandom`)

---

## 9. Multi-tenancy (scope kraju)

```java
// Kontroler wyciąga scope z tokenu:
String countryScope = getCountryScope(authentication);  // null = brak ograniczenia (ADMIN)

// Serwis filtruje po kraju:
if (countryScope != null) {
    return repository.findAllByCountry(normalizeCountryCode(countryScope));
}
return repository.findAll();
```

**Zasady:**
- `ADMIN` ma scope `null` — widzi wszystkie kraje
- `TECHNICAL` ma scope przypisany do konta — widzi tylko swój kraj
- Zawsze normalizuj kod kraju: `trim().toUpperCase(Locale.ROOT)`
- Dostępne kody konfigurowane w `app.available-country-codes` (domyślnie `PL`)

---

## 10. Migracje bazy danych (Liquibase)

**Zasady:**
- Master changelog: `src/main/resources/db/changelog-master.xml`
- Pliki SQL w: `src/main/resources/db/migrations/NNN_opis.sql`
- Numeracja: `001`, `002`, ... — ściśle rosnąca
- Autor changeset: `author="codex"`
- Każda migracja: addytywna i idempotentna (nie modyfikuj istniejących changesetów)
- Indeksy: osobny changeset `003_indexes_and_base_constraints.sql`

---

## 11. Testy

**Testy jednostkowe (serwisy):**
```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {
    @Mock private MyRepository myRepository;
    @InjectMocks private MyService myService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(myService, "availableCountryCodesConfig", "PL,DE");
    }

    @Test
    void doSomething_validInput_shouldReturnResult() {
        when(myRepository.findById(1L)).thenReturn(Optional.of(entity));
        MyEntity result = myService.doSomething(1L);
        assertThat(result).isNotNull();
    }

    @Test
    void doSomething_invalidInput_shouldThrow() {
        assertThatThrownBy(() -> myService.doSomething(null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("...");
    }
}
```

**Testy kontrolerów:**
```java
@WebMvcTest(value = MyController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class})
class MyControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean MyService myService;
    @MockBean JwtService jwtService;
    @MockBean AdminUserDetailsService adminUserDetailsService;
    @MockBean StoreUserDetailsService storeUserDetailsService;
    @MockBean EcomUserDetailsService ecomUserDetailsService;
}
```

**Zasady:**
- Cel pokrycia linii: **≥ 90%** dla klas serwisów i kontrolerów
- Klasy wykluczone z pokrycia w SonarQube: `**/config/**`, `LoyaltyClubApplication.java`
- AssertJ do asercji (`assertThat`, `assertThatThrownBy`) — nie JUnit `assertEquals`
- Mockito: `when(...).thenReturn(...)`, `verify(...)`, `thenAnswer(inv -> inv.getArgument(0))`
- `UnnecessaryStubbingException`: nie stubuj metod, które nie są wywoływane w danym teście
- Wynik `isInstanceOf`: używaj konkretnego wyjątku (`BusinessException.class`) — nie `RuntimeException.class`
- Dla `@Value` w testach: `ReflectionTestUtils.setField(service, "fieldName", "value")`
- Kontrolery testuj bez autokonfiguracji Security — mockuj cztery beany: `JwtService`, `AdminUserDetailsService`, `StoreUserDetailsService`, `EcomUserDetailsService`

---

## 12. SonarQube / jakość kodu

**Zasady eliminujące CODE_SMELL:**
- Nie używaj `Stream.collect(Collectors.toList())` — używaj `.toList()` (Java 16+)
- Nie rzucaj gołych `RuntimeException` — używaj `BusinessException` lub `ResourceNotFoundException`
- Duplikaty kodu → ekstrahuj do wspólnej klasy (`util/`, `model/`)
- Nie używaj `ThreadLocalRandom`/`Random` dla danych bezpieczeństwa — `SecureRandom`
- Złożoność kognitywna metody: ≤ 15 — ekstrahuj prywatne metody pomocnicze
- Łącz wielokrotne `assertThat(x)` w jedną chwańcuchowaną asercję
- Nie używaj `eq(value)` w Mockito gdy wartość nie jest matcherem — podawaj wartość bezpośrednio
- Nie używaj `Thread.sleep()` w testach — symuluj stan poprzez parametry (np. `jwtExpirationMs = -1L`)

**CI/CD:**
- Pipeline: `jenkins/build.jenkinsfile`
- Kolejność etapów: Checkout → Testy → SonarQube → Build → Stop → Archive → Deploy → Start
- Token SonarQube: Jenkins credential `loyalty-club` (typ: Secret text)
- Testy: `mvn test -P build-backend`
- Build: `mvn clean package -DskipTests`
- Analiza: `mvn sonar:sonar -Dsonar.login=${SONAR_TOKEN}`

---

## 13. Wzorce architektoniczne

**Seeder (inicjalizacja danych):**
```java
@Component @RequiredArgsConstructor
public class MySeeder implements CommandLineRunner {
    @Override
    public void run(String... args) {
        repository.findByUsername("x").orElseGet(() ->
            repository.save(Entity.builder().build())
        );
    }
}
```
Zasada: `orElseGet()` zapewnia idempotentność — bezpieczne przy każdym starcie.

**Idempotency key:**
- Dla operacji finansowych/kuponowych: nagłówek `Idempotency-Key`
- Przechowuj w tabeli `coupon_redemption_requests` z unikalnym kluczem
- Przy ponownym żądaniu z tym samym kluczem: zwróć pierwotny wynik

**Shared utility component:**
```java
@Component
public class MyUtility {
    private final SecureRandom secureRandom = new SecureRandom();
    // SecureRandom jako pole klasy (nie per-request) — thread-safe i kosztowny w init
}
```
