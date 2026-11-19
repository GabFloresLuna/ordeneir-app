package cl.floresluna.ordenier.auth.dto;

public record TokenResponse(String accessToken, long expiresInSeconds) {
}
