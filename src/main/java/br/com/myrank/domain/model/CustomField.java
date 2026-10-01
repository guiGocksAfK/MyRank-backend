package br.com.myrank.domain.model;

import br.com.myrank.domain.enums.CustomFieldType;

/** Definição imutável: o ID associa os valores ao campo mesmo após renomeá-lo. */
public record CustomField(String id, String name, CustomFieldType type) {}
