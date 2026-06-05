package com.ssafy.wswg.model.dto;


import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class UserDto {
    private Long id;
    private String name;
    private String email;
    private Role role;

    private LocalDate createdAt;

    @Builder
    public UserDto(String email, String name, Role role) {
        this.email = email;
        this.name = name;
        this.role = role;
        createdAt = LocalDate.now();
    }
}
