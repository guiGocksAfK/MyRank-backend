package br.com.myrank.service.email;

import org.springframework.web.util.HtmlUtils;

/**
 * Layout único dos emails do MyRank: faixa preta com o logo (My branco, Rank
 * dourado), corpo claro, botão em pílula dourada, link de reserva discreto e
 * rodapé cinza. Feito com tabelas e estilo inline porque é o que os apps de
 * email respeitam; o corpo é claro pra não quebrar no modo escuro do Gmail/Outlook.
 *
 * Todo texto passa por escape; quem chama manda texto puro.
 */
public final class EmailLayout {

    private static final String FONT =
            "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    private EmailLayout() {}

    /**
     * @param preheader     linha que aparece ao lado do assunto na caixa de entrada
     * @param greeting      "Olá, fulano!" (já com o nome)
     * @param intro         parágrafo antes do botão
     * @param button        texto do botão (null = sem botão)
     * @param link          destino do botão (null = sem botão e sem link de reserva)
     * @param code          código em destaque no lugar do botão (pode ser null)
     * @param outro         parágrafo depois do botão (pode ser null)
     * @param safetyTitle   título do aviso "não foi você?" (em destaque)
     * @param safetyText    texto do aviso: sem clicar no botão, nada acontece
     * @param note          observação curta, ex.: validade do link (pode ser null)
     * @param fallbackLabel "O botão não funcionou? Copie este link:"
     * @param footer        por que a pessoa recebeu o email
     */
    public record Content(String preheader, String greeting, String intro, String button, String link,
                          String code, String outro, String safetyTitle, String safetyText, String note,
                          String fallbackLabel, String footer) {}

    public static String render(Content c) {
        String link = esc(c.link());
        // ação principal: botão dourado, código em destaque, ou nada (email só de aviso)
        String action;
        if (c.code() != null) {
            action = """
                    <p style="margin:24px 0;padding:16px 20px;background:#f7f7f7;border-radius:10px;text-align:center;font-family:'SFMono-Regular',Consolas,'Liberation Mono',monospace;font-size:30px;font-weight:700;letter-spacing:6px;color:#111111">%s</p>
                    """.formatted(esc(c.code()));
        } else if (c.link() != null) {
            action = """
                    <table role="presentation" cellpadding="0" cellspacing="0" style="margin:26px 0">
                      <tr>
                        <td style="background:#d4af37;border-radius:999px">
                          <a href="%s" style="display:inline-block;padding:14px 30px;font-family:%s;font-size:15px;font-weight:700;color:#111111;text-decoration:none;border-radius:999px">%s</a>
                        </td>
                      </tr>
                    </table>
                    """.formatted(link, FONT, esc(c.button()));
        } else {
            action = "<div style=\"height:12px\"></div>";
        }
        String fallback = c.link() == null ? "" : """
                %s<br>
                <a href="%s" style="color:#999999;word-break:break-all">%s</a>
                <p style="margin:14px 0 0">%s</p>
                """.formatted(esc(c.fallbackLabel()), link, link, esc(c.footer()));
        String footer = c.link() == null
                ? "<p style=\"margin:0\">%s</p>".formatted(esc(c.footer()))
                : fallback;
        String outro = c.outro() == null ? "" : """
                <p style="margin:0 0 16px;font-size:15px;line-height:1.6;color:#333333">%s</p>
                """.formatted(esc(c.outro()));
        // aviso de segurança em destaque: caixinha clara com borda dourada
        String safety = c.safetyTitle() == null ? "" : """
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin:4px 0 20px">
                  <tr>
                    <td style="background:#f7f7f7;border-left:3px solid #d4af37;border-radius:6px;padding:14px 16px;font-size:14px;line-height:1.55;color:#333333">
                      <strong style="color:#111111">%s</strong> %s
                    </td>
                  </tr>
                </table>
                """.formatted(esc(c.safetyTitle()), esc(c.safetyText()));
        String note = c.note() == null ? "" : """
                <p style="margin:0 0 8px;font-size:13px;line-height:1.5;color:#777777">%s</p>
                """.formatted(esc(c.note()));

        return """
                <!doctype html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                </head>
                <body style="margin:0;padding:0;background:#f2f2f2">
                  <span style="display:none;max-height:0;max-width:0;overflow:hidden;opacity:0;color:transparent">%s</span>
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f2f2f2">
                    <tr>
                      <td align="center" style="padding:32px 16px">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                               style="max-width:520px;background:#ffffff;border-radius:14px;overflow:hidden">
                          <tr>
                            <td style="background:#000000;padding:26px 32px;font-family:%s;font-size:24px;font-weight:800;color:#ffffff">
                              My<span style="color:#d4af37">Rank</span>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:32px 32px 12px;font-family:%s">
                              <p style="margin:0 0 12px;font-size:20px;font-weight:700;color:#111111">%s</p>
                              <p style="margin:0 0 4px;font-size:15px;line-height:1.6;color:#333333">%s</p>
                              %s
                              %s
                              %s
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:18px 32px 26px;border-top:1px solid #eeeeee;font-family:%s;font-size:12px;line-height:1.6;color:#999999">
                              %s
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                esc(c.preheader()),
                FONT,
                FONT, esc(c.greeting()), esc(c.intro()),
                action,
                outro, safety, note,
                FONT, footer);
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
