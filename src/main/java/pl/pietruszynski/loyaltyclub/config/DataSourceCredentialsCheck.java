package pl.pietruszynski.loyaltyclub.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Sprawdza przy starcie, ze haslo bazy danych pochodzi z konfiguracji srodowiska.
 *
 * <p>Wlasciwosc {@code spring.datasource.password} nie ma wartosci domyslnej:
 * w application.properties zapisano ja jako {@code ${DB_PASSWORD}}, a plik
 * Compose przerywa podnoszenie uslugi, gdy zmiennej brakuje. Samo pominiecie
 * wartosci domyslnej nie wystarcza jednak po stronie aplikacji. Wlasciwosci
 * zrodla danych wiazane sa mechanizmem {@code @ConfigurationProperties}, ktory
 * nierozwiazanego placeholdera nie zglasza jako bledu, tylko przekazuje dalej
 * jego tresc - aplikacja probowalaby wtedy zalogowac sie do bazy haslem
 * "${DB_PASSWORD}" i przewrocila sie dopiero na odmowie polaczenia, komunikatem
 * nieprowadzacym do przyczyny. Adnotacja {@code @Value} zachowuje sie inaczej
 * i dlatego wystarcza kluczowi podpisu tokenow oraz haslom kont poczatkowych.
 *
 * <p>Sprawdzenie wykonuje sie jako {@link BeanFactoryPostProcessor}, a wiec
 * zanim powstanie pula polaczen: zwykly komponent utworzony bylby juz po
 * pierwszej probie polaczenia, wywolanej budowa fabryki encji.
 *
 * <p>Obejmuje wylacznie kontekst, w ktorym zrodlo danych w ogole powstaje
 * (testy warstwy sieciowej go nie maja) i wskazuje na PostgreSQL, czyli baze
 * wdrozenia. Profil testowy laczy sie z baza H2 w pamieci, tworzona na czas
 * jednego przebiegu i przyjmujaca puste haslo - tam wymog nie ma sensu.
 *
 * <p>Awaria przy starcie jest tansza niz srodowisko dzialajace z
 * poswiadczeniem, ktorego nikt swiadomie nie wybral.
 */
@Component
public class DataSourceCredentialsCheck implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final String DATA_SOURCE_BEAN = "dataSource";
    private static final String URL_PROPERTY = "spring.datasource.url";
    private static final String PASSWORD_PROPERTY = "spring.datasource.password";
    private static final String POSTGRES_URL_PREFIX = "jdbc:postgresql:";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (environment == null || !beanFactory.containsBeanDefinition(DATA_SOURCE_BEAN)) {
            return;
        }
        String url = environment.getProperty(URL_PROPERTY);
        if (url == null || !url.startsWith(POSTGRES_URL_PREFIX)) {
            return;
        }
        if (isBlank(resolvePassword())) {
            throw new IllegalStateException(
                    "Haslo bazy danych nie zostalo podane. Ustaw zmienna srodowiskowa DB_PASSWORD "
                            + "(we wdrozeniu kontenerowym: POSTGRES_PASSWORD w pliku .env). Haslo nie ma "
                            + "wartosci domyslnej celowo -- patrz .env.example.");
        }
    }

    /**
     * Zwraca haslo albo {@code null}, gdy zmiennej brakuje. Przy nierozwiazanym
     * placeholderze rozwiazywanie wlasciwosci konczy sie wyjatkiem - z punktu
     * widzenia tego sprawdzenia jest to ten sam przypadek co brak wartosci.
     */
    private String resolvePassword() {
        try {
            return environment.getProperty(PASSWORD_PROPERTY);
        } catch (IllegalArgumentException nierozwiazanyPlaceholder) {
            return null;
        }
    }

    private static boolean isBlank(String password) {
        return password == null || password.isBlank() || password.contains("${");
    }
}
