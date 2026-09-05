package trading_api.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) { this.users = users; this.passwordEncoder = passwordEncoder; this.jwtService = jwtService; }
    @Transactional
    public User register(AuthDtos.RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) throw new EmailAlreadyExistsException();
        User user = new User(); user.setEmail(email); user.setPasswordHash(passwordEncoder.encode(request.password())); user.setFullName(request.fullName());
        return users.save(user);
    }
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim()).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) throw new InvalidCredentialsException();
        return new AuthDtos.AuthResponse(jwtService.generate(user), user.getId(), user.getEmail(), user.getFullName());
    }
    public static class EmailAlreadyExistsException extends RuntimeException {}
    public static class InvalidCredentialsException extends RuntimeException {}
}