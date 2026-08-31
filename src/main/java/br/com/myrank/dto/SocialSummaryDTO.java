package br.com.myrank.dto;

public record SocialSummaryDTO(
        long following,
        long followers,
        long feedCount,
        long pendingFollowRequests
) {}
