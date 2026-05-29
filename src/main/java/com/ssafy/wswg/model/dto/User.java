package com.ssafy.wswg.model.dto;


import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    private int id;
    private String name;
    private String email;
    private Role role;

    private LocalDate createdAt;
}
