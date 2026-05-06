package com.akash.budgetchanger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "Question cannot be empty")
    @Size(min = 2, max = 1000, message = "Question must be between 2 and 1000 characters")
    private String question;
}
