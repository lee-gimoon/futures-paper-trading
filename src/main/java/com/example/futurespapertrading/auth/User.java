package com.example.futurespapertrading.auth;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

// R2DBC 엔티티(record). users 테이블과 1:1.
// 스네이크케이스 컬럼은 @Column으로 명시 매핑한다.
// created_at/updated_at은 DB DEFAULT now()가 채우므로 엔티티에서 다루지 않는다.
@Table("users")
public record User(
        @Id Long id,
        String email,
        @Column("password_hash") String passwordHash,
        @Column("display_name") String displayName
) {
}
