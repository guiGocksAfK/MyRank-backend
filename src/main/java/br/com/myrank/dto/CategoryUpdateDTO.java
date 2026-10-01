package br.com.myrank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import br.com.myrank.domain.enums.TableTemplate;

import java.util.List;

public class CategoryUpdateDTO {

    @NotBlank(message = "Informe o nome da tabela.")
    @Size(max = 60, message = "O nome deve ter no máximo 60 caracteres.")
    private String name;

    /** null mantém os templates; adicionar pode sempre, remover só se nenhum item usar. */
    @Size(max = 10, message = "Escolha no máximo 10 tipos.")
    private List<TableTemplate> templates;

    public CategoryUpdateDTO() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<TableTemplate> getTemplates() { return templates; }
    public void setTemplates(List<TableTemplate> templates) { this.templates = templates; }
}
