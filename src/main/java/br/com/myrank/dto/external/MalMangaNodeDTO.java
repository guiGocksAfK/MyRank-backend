package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Detalhes de mangá; reaproveita título, imagem e data já tratados para anime. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MalMangaNodeDTO extends MalAnimeNodeDTO {

    private List<Author> authors;

    @JsonProperty("num_volumes")
    private Integer numVolumes;

    public List<Author> getAuthors() { return authors; }
    public void setAuthors(List<Author> authors) { this.authors = authors; }

    public Integer getNumVolumes() { return numVolumes; }
    public void setNumVolumes(Integer numVolumes) { this.numVolumes = numVolumes; }

    /** Nomes dos autores separados por vírgula; null se nenhum nome estiver disponível. */
    public String resolveAuthorNames() {
        if (authors == null || authors.isEmpty()) return null;
        String names = authors.stream()
                .filter(Objects::nonNull)
                .map(Author::getNode)
                .filter(Objects::nonNull)
                .map(AuthorNode::resolveName)
                .filter(name -> !name.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        return names.isEmpty() ? null : names;
    }

    /** A API informa cada autor em um bloco { node, role }. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Author {
        private AuthorNode node;

        public AuthorNode getNode() { return node; }
        public void setNode(AuthorNode node) { this.node = node; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AuthorNode {
        @JsonProperty("first_name")
        private String firstName;

        @JsonProperty("last_name")
        private String lastName;

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        private String resolveName() {
            return Stream.of(firstName, lastName)
                    .filter(name -> name != null && !name.isBlank())
                    .map(String::trim)
                    .collect(Collectors.joining(" "));
        }
    }
}
