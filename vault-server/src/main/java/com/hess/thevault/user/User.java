package com.hess.thevault.user;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import org.antlr.v4.runtime.misc.NotNull;

import java.util.UUID;

@Entity
@Data
public class User {
    @NotNull
    @Id
    private UUID id;
}
