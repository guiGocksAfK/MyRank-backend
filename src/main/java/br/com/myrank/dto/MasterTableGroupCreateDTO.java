package br.com.myrank.dto;

import java.util.List;

public record MasterTableGroupCreateDTO(
        String name,
        List<Long> categoryIds
) {}