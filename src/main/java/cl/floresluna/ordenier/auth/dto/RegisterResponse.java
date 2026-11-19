package cl.floresluna.ordenier.auth.dto;

public record RegisterResponse(long id, String email, String accessToken, long expiresInSeconds) {
}
