package com.example.library.dto;
import jakarta.validation.constraints.*;
import java.util.List;
public class LendingRequest {
    @NotNull private Long userId;
    @NotNull @Size(min=1, max=5) private List<Long> bookIds;
}
