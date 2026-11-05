package br.com.myrank.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreatorNamesTest {

    @Test
    void separa_naVirgulaDaApi() {
        assertThat(CreatorNames.split("Vince Gilligan, Peter Gould"))
                .containsExactly("Vince Gilligan", "Peter Gould");
    }

    @Test
    void separa_noPontoEVirgulaENoEComercial_deQuemDigitaAMao() {
        assertThat(CreatorNames.split("Wachowski; Lana & Lilly"))
                .containsExactly("Wachowski", "Lana", "Lilly");
    }

    @Test
    void naoQuebra_noE_porqueApareceEmNomeDeEstudio() {
        assertThat(CreatorNames.split("Kyoto Animation e Amigos")).containsExactly("Kyoto Animation e Amigos");
    }

    @Test
    void ignoraVazios_espacosSobrandoERepetidos() {
        assertThat(CreatorNames.split("  Studio  Pierrot ,, studio pierrot ,")).containsExactly("Studio Pierrot");
        assertThat(CreatorNames.split("   ")).isEmpty();
        assertThat(CreatorNames.split(null)).isEmpty();
    }

    @Test
    void chave_ignoraAcentoMaiusculaEEspacos() {
        assertThat(CreatorNames.key(" Hayao  Miyazáki ")).isEqualTo(CreatorNames.key("hayao miyazaki"));
        assertThat(CreatorNames.keys("David Benioff, D. B. Weiss")).containsExactly("david benioff", "d. b. weiss");
    }
}
