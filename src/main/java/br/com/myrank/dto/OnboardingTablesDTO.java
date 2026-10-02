package br.com.myrank.dto;

import br.com.myrank.domain.enums.TableTemplate;

import java.util.List;

public record OnboardingTablesDTO(List<TableTemplate> templates) {}
