package com.burmalda.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A user's question to AI Burmalda and the answer — for the chat history. */
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
