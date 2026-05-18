package dev.themajorones.autotest.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import dev.themajorones.models.entity.GitHubOwnerMembership;

@Repository
public interface GitHubOwnerMembershipRepository extends JpaRepository<GitHubOwnerMembership, Integer> {

    List<GitHubOwnerMembership> findAllByUserId(Integer userId);

    boolean existsByUserIdAndOwnerId(Integer userId, Integer ownerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GitHubOwnerMembership membership where membership.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Integer userId);
}
