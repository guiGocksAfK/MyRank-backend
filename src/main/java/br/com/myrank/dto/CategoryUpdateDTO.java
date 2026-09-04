package br.com.myrank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CategoryUpdateDTO {

    @NotBlank(message = "Informe o nome da tabela.")
    @Size(max = 60, message = "O nome deve ter no máximo 60 caracteres.")
    private String name;

    public CategoryUpdateDTO() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
