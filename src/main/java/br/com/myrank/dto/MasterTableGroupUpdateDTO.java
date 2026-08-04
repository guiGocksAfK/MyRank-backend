package br.com.myrank.dto;

import java.util.List;

public record MasterTableGroupUpdateDTO(
        String name,
        List<Long> categoryIds
) {}