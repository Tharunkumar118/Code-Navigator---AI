package com.codenavigator.ai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING) // store "ROLE_STUDENT" text, not ordinal int -> safe if enum order changes
    @Column(length = 30, unique = true, nullable = false)
    private ERole name;

    public Role(ERole name) {
        this.name = name;
    }
}
