package com.trynoice.api.identity;

import com.trynoice.api.data.BasicEntity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Calendar;
import java.util.List;

@Entity
@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AuthUser extends BasicEntity<Integer> {

    @NonNull
    private String email;

    @NonNull
    private String name;

    @Temporal(TemporalType.TIMESTAMP)
    @NonNull
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private Calendar lastActiveAt = Calendar.getInstance();

    @OneToMany(mappedBy = "owner", fetch = FetchType.LAZY)
    private List<RefreshToken> refreshTokens;

    @PrePersist
    @PreUpdate
    void setLastActiveAt() {
        this.lastActiveAt = Calendar.getInstance();
    }
}
