import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
public class SecurityConfig { //} extends WebSecurityConfigurerAdapter {

//    @Override
//    protected void configure(HttpSecurity http) throws Exception {
//        http
//                .headers()
//                .frameOptions().disable() // Disable X-Frame-Options header
//                .and()
//                .authorizeRequests()
//                .anyRequest().permitAll(); // Allow all requests (adjust as needed)
//    }

//    @Bean
//    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//        http
//                .authorizeHttpRequests(auth -> auth
//                        .requestMatchers("/public").permitAll() // Allow public access
//                        .anyRequest().authenticated()  // Require authentication for all other endpoints
//                )
//                .httpBasic(); // Enable Basic Authentication
//
//        return http.build();
//    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF protection completely
                .cors(cors -> cors.disable()) // Disable CORS in security (handled by @CrossOrigin)
                .sessionManagement(session -> session.disable()) // Disable session management
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll() // Allow ALL requests without any authentication
                )
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.disable()) // Disable X-Frame-Options header
                )
                .httpBasic(httpBasic -> httpBasic.disable()) // Disable HTTP Basic auth
                .formLogin(formLogin -> formLogin.disable()) // Disable form login
                .logout(logout -> logout.disable()); // Disable logout

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.builder()
                .username("oracleai")
                .password(passwordEncoder().encode("oracleai"))
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(user);
    }
}
