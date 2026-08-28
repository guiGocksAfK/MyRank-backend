package br.com.myrank.dto;

/** mine: "up" | "agree" | "disagree" | null */
public record ReactionSummaryDTO(long up, long agree, long disagree, String mine) {}
