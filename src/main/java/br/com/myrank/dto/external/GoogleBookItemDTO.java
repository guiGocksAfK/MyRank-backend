package br.com.myrank.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleBookItemDTO {

    private String id;

    @JsonProperty("volumeInfo")
    private GoogleBookVolumeInfoDTO volumeInfo;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public GoogleBookVolumeInfoDTO getVolumeInfo() { return volumeInfo; }
    public void setVolumeInfo(GoogleBookVolumeInfoDTO volumeInfo) { this.volumeInfo = volumeInfo; }
}