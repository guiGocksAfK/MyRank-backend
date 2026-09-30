package br.com.myrank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import br.com.myrank.domain.enums.TableTemplate;

public class CategoryCreateDTO {

    @NotBlank(message = "Informe o nome da tabela.")
    @Size(max = 60, message = "O nome deve ter no máximo 60 caracteres.")
    private String name;

    private TableTemplate template = TableTemplate.CUSTOM;

    public CategoryCreateDTO() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public TableTemplate getTemplate() { return template; }
    public void setTemplate(TableTemplate template) { this.template = template; }
}
