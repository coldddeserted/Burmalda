package com.burmalda.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Вопрос пользователя к AI Burmalda и ответ — для истории чата. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User user;

    @Column(length = 2000, nullable = false)
    private String question;

    @Column(length = 8000)
    private String reply;

    private LocalDateTime createdAt = LocalDateTime.now();
}
