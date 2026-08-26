package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbCreditsDTO {

    private List<TmdbCrewMemberDTO> crew;

    public List<TmdbCrewMemberDTO> getCrew() { return crew; }
    public void setCrew(List<TmdbCrewMemberDTO> crew) { this.crew = crew; }

    /** Procura o primeiro membro da equipe cujo cargo seja "Director". */
    public String findDirectorName() {
        if (crew == null) return null;
        return crew.stream()
                .filter(c -> "Director".equalsIgnoreCase(c.getJob()))
                .map(TmdbCrewMemberDTO::getName)
                .findFirst()
                .orElse(null);
    }
}