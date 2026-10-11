package com.doppsy.repository;

import com.doppsy.entity.User;
import com.doppsy.entity.User.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link User} entities.
 *
 * <p>Extends {@link JpaRepository} to inherit full CRUD operations,
 * pagination, and sorting out of the box. Custom derived-query methods
 * are declared below for the most common access patterns.</p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds a user by their mobile number (the login identifier).
     *
     * @param mobileNumber E.164-format mobile number
     * @return an {@link Optional} containing the user if found
     */
    Optional<User> findByMobileNumber(String mobileNumber);

    /**
     * Checks whether a mobile number is already registered.
     *
     * @param mobileNumber E.164-format mobile number
     * @return {@code true} if the number is taken
     */
    boolean existsByMobileNumber(String mobileNumber);

    /**
     * Returns all users with the given role (e.g., all ADMINs).
     *
     * @param role the {@link UserRole} to filter by
     * @return list of matching users
     */
    List<User> findAllByRole(UserRole role);

    /**
     * Returns all users whose full name contains the given search term,
     * case-insensitive. Useful for admin search panels.
     *
     * @param name partial or full name to search
     * @return list of matching users
     */
    List<User> findByFullNameContainingIgnoreCase(String name);
}
