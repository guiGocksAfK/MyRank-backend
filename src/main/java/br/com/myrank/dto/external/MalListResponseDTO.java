package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Resposta de lista da API oficial do MyAnimeList (busca e ranking):
 * { "data": [ { "node": { ... } }, ... ], "paging": { ... } }.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MalListResponseDTO {

    private List<Entry> data;

    public List<Entry> getData() { return data; }
    public void setData(List<Entry> data) { this.data = data; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Entry {
        private MalAnimeNodeDTO node;

        public MalAnimeNodeDTO getNode() { return node; }
        public void setNode(MalAnimeNodeDTO node) { this.node = node; }
    }
}
