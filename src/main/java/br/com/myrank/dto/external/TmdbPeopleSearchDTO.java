package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** GET /search/person e /search/company da TMDB: só o que a foto do criador precisa. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbPeopleSearchDTO {

    private List<Result> results;

    public List<Result> getResults() { return results; }
    public void setResults(List<Result> results) { this.results = results; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String name;
        @JsonProperty("profile_path") private String profilePath;
        @JsonProperty("logo_path") private String logoPath;
        @JsonProperty("known_for_department") private String knownForDepartment;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getProfilePath() { return profilePath; }
        public void setProfilePath(String profilePath) { this.profilePath = profilePath; }
        public String getLogoPath() { return logoPath; }
        public void setLogoPath(String logoPath) { this.logoPath = logoPath; }
        public String getKnownForDepartment() { return knownForDepartment; }
        public void setKnownForDepartment(String knownForDepartment) { this.knownForDepartment = knownForDepartment; }
    }
}
