package ChefBot_Telegram.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TelegramHtmlUtilTest {

    @Test
    @DisplayName("Deve escapar corretamente caracteres especiais do HTML (&, <, >)")
    void shouldEscapeHtmlSpecialCharacters() {
        assertEquals("Bolo &amp; Torta", TelegramHtmlUtil.escapeHtml("Bolo & Torta"));
        assertEquals("&lt;script&gt;alert('xss')&lt;/script&gt;", TelegramHtmlUtil.escapeHtml("<script>alert('xss')</script>"));
        assertEquals("1 &lt; 2 &amp;&amp; 3 &gt; 2", TelegramHtmlUtil.escapeHtml("1 < 2 && 3 > 2"));
    }

    @Test
    @DisplayName("Deve retornar string vazia ao escapar null")
    void shouldReturnEmptyStringOnNull() {
        assertEquals("", TelegramHtmlUtil.escapeHtml(null));
    }

    @Test
    @DisplayName("Deve envolver com tags <b> e escapar texto interno")
    void shouldFormatBold() {
        assertEquals("<b>Bolo &amp; Pudim</b>", TelegramHtmlUtil.bold("Bolo & Pudim"));
    }

    @Test
    @DisplayName("Deve envolver com tags <i> e escapar texto interno")
    void shouldFormatItalic() {
        assertEquals("<i>por Vovó &amp; Vovô</i>", TelegramHtmlUtil.italic("por Vovó & Vovô"));
    }

    @Test
    @DisplayName("Deve envolver com tags <code> e escapar texto interno")
    void shouldFormatCode() {
        assertEquals("<code>/atualizar 1 &lt;cmd&gt;</code>", TelegramHtmlUtil.code("/atualizar 1 <cmd>"));
    }

    @Test
    @DisplayName("Deve remover todas as tags HTML no stripHtml")
    void shouldStripHtmlTags() {
        String html = "<b>Olá</b>, <i>amigo</i>! <code>/ajuda</code>";
        assertEquals("Olá, amigo! /ajuda", TelegramHtmlUtil.stripHtml(html));
    }

    @Test
    @DisplayName("stripHtml deve lidar com null de forma segura")
    void shouldStripHtmlNullSafe() {
        assertEquals("", TelegramHtmlUtil.stripHtml(null));
    }
}
