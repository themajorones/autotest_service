package dev.themajorones.ats.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.themajorones.ats.entity.AppRefreshToken;

public interface AppRefreshTokenRepository extends JpaRepository<AppRefreshToken, Integer> {

    Optional<AppRefreshToken> findByToken(String token);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AppRefreshToken token set token.revokedAt = :revokedAt where token.userId = :userId and token.revokedAt is null")
    int revokeAllActiveByUserId(@Param("userId") Integer userId, @Param("revokedAt") Long revokedAt);
}
