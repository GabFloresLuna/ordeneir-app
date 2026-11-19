package cl.floresluna.ordenier.auth;

import cl.floresluna.ordenier.auth.dto.RegisterRequest;
import cl.floresluna.ordenier.auth.dto.RegisterResponse;
import cl.floresluna.ordenier.common.EmailAlreadyRegisteredException;
import cl.floresluna.ordenier.security.JwtService;
import cl.floresluna.ordenier.user.User;
import cl.floresluna.ordenier.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	// Sin @Transactional a propósito (plan D5): la restricción UNIQUE salta al guardar
	// y la excepción se captura aquí, fuera de cualquier transacción.
	public RegisterResponse register(RegisterRequest request) {
		User user = new User(request.email(), passwordEncoder.encode(request.password()));
		try {
			user = userRepository.save(user);
		} catch (DataIntegrityViolationException e) {
			throw new EmailAlreadyRegisteredException();
		}
		String token = jwtService.issueToken(user.getId());
		return new RegisterResponse(user.getId(), user.getEmail(), token, JwtService.EXPIRES_IN_SECONDS);
	}
}
