package br.com.myrank.dto;

import jakarta.validation.constraints.Size;
import br.com.myrank.domain.enums.TableTemplate;

import java.util.List;

public class CategoryUpdateDTO {

    /** null mantém o nome atual; se enviado, não pode ficar em branco (validado no service). */
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
