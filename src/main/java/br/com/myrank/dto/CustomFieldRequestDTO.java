package br.com.myrank.dto;

import br.com.myrank.domain.enums.CustomFieldType;

/** ID ausente cria um campo; ID informado precisa pertencer à tabela. */
public record CustomFieldRequestDTO(String id, String name, CustomFieldType type) {}
