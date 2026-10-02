package br.com.myrank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import br.com.myrank.domain.enums.TableTemplate;

import java.util.List;

public class CategoryCreateDTO {

    @NotBlank(message = "Informe o nome da tabela.")
    @Size(max = 60, message = "O nome deve ter no máximo 60 caracteres.")
    private String name;

    /** Um ou mais; vazio ou ausente = Personalizado. */
    @Size(max = 10, message = "Escolha no máximo 10 tipos.")
    private List<TableTemplate> templates;

    public CategoryCreateDTO() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<TableTemplate> getTemplates() { return templates; }
    public void setTemplates(List<TableTemplate> templates) { this.templates = templates; }
}
