package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Base user entity — parent of Doctor and Patient via JOINED inheritance.
 * Stores authentication credentials and common profile fields.
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_user_email", columnList = "email"),
        @Index(name = "idx_user_role",  columnList = "role")
    }
)
@Inheritance(strategy = InheritanceType.JOINED)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(unique = true, nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum Role {
        PATIENT, DOCTOR, ADMIN
    }

    // ── Explicit accessors (complement Lombok @Getter for compiler reliability) ─

    public UUID    getId()            { return id; }
    public String  getEmail()         { return email; }
    public String  getPasswordHash()  { return passwordHash; }
    public Role    getRole()          { return role; }
    public String  getFirstName()     { return firstName; }
    public String  getLastName()      { return lastName; }
    public String  getPhone()         { return phone; }
    public Boolean getEnabled()       { return enabled; }
    public Boolean getEmailVerified() { return emailVerified; }
}

