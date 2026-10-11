# 🎯 TASK 07: Robustez no Telegram — Formatação HTML Segura e Suporte a Arquivos de Áudio

## 📌 Contexto e Problema
1. **Quebra de Envio por Markdown Reservado:**
   No método `TelegramBotService.sendTextMessage`:
   ```java
   SendMessage message = SendMessage.builder()
       .chatId(chatId.toString())
       .text(text)
       .parseMode("Markdown")
       .build();
   ```
   Se uma receita gerada pela IA ou digitada pelo usuário contiver caracteres especiais (`_`, `*`, `[`, `]`, `(`, `)`, `~`, `` ` ``, `>`, `#`, `+`, `-`, `=`, `|`, `{`, `}`, `.`, `!`), a API do Telegram rejeita a mensagem com o erro:
   `Bad Request: can't parse entities: Character ... is reserved`.
   O fallback atual faz `text.replace("*", "").replace("`", "").replace("_", "")`, mas se houver colchetes ou caracteres reservados restantes, a mensagem simplesmente falha e o usuário não recebe nenhuma resposta!
   A forma padrão e mais robusta na API do Telegram é usar **HTML** (`<b>`, `<i>`, `<code>`) ou sanitizar rigorosamente os caracteres reservados.
2. **Ignorar Arquivos de Áudio (`message.hasAudio()`):**
   No método `TelegramBotService.consume`:
   O bot checa apenas `if (message.hasVoice())`.
   - `message.hasVoice()` pega apenas notas de voz gravadas direto no microfone do Telegram (formato `.ogg`).
   - Se o usuário encaminhar um áudio do WhatsApp, enviar um arquivo de voz gravado em outro app ou enviar um arquivo de áudio (`.mp3`, `.m4a`, `.aac`), o bot não detecta (`message.hasAudio()` é ignorado) e o usuário fica sem nenhuma resposta.

---

## 🎯 Objetivos da Task
1. Migrar a formatação das mensagens do Telegram de Markdown v1 frágil para **HTML** (`<b>`, `<i>`, `<code>`, `<pre>`) com sanitizador de caracteres (`&`, `<`, `>`).
2. Adicionar suporte ao recebimento de arquivos de áudio enviados como documento de mídia (`message.hasAudio()`), além de `hasVoice()`.
3. Criar testes unitários para o formatador HTML em `TelegramHtmlFormatterTest.java`.

---

## 📂 Arquivos Envolvidos
- `ChefBot_Telegram/src/main/java/ChefBot_Telegram/service/TelegramBotService.java`
- `ChefBot_Telegram/src/main/java/ChefBot_Telegram/service/RecipeService.java`
- `ChefBot_Telegram/src/main/java/ChefBot_Telegram/util/TelegramHtmlUtil.java` (criar)
- `ChefBot_Telegram/src/test/java/ChefBot_Telegram/util/TelegramHtmlUtilTest.java` (criar)

---

## 🔨 Instruções Passo a Passo para a LLM

### Passo 1: Criar `TelegramHtmlUtil.java`
Crie uma classe utilitária para sanitizar texto antes de aplicar tags HTML:
```java
package ChefBot_Telegram.util;

public final class TelegramHtmlUtil {

    private TelegramHtmlUtil() {}

    public static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }

    public static String bold(String text) {
        return "<b>" + escapeHtml(text) + "</b>";
    }

    public static String italic(String text) {
        return "<i>" + escapeHtml(text) + "</i>";
    }

    public static String code(String text) {
        return "<code>" + escapeHtml(text) + "</code>";
    }
}
```

### Passo 2: Atualizar `RecipeService.formatRecipeForTelegram` para HTML
Em vez de Markdown, gere o texto em HTML seguro:
```java
public String formatRecipeForTelegram(Recipe recipe) {
    StringBuilder sb = new StringBuilder();

    sb.append("🍽️ <b>").append(TelegramHtmlUtil.escapeHtml(recipe.getTitle()))
      .append("</b> <code>(ID: #").append(recipe.getId()).append(")</code>\n\n");

    if (recipe.getSourceAuthor() != null && !recipe.getSourceAuthor().isBlank()) {
        sb.append("👤 <b>Ensinado por:</b> ").append(TelegramHtmlUtil.escapeHtml(recipe.getSourceAuthor())).append("\n");
    }
    if (recipe.getCategory() != null && !recipe.getCategory().isBlank()) {
        sb.append("📂 <b>Categoria:</b> ").append(TelegramHtmlUtil.escapeHtml(recipe.getCategory())).append("\n");
    }
    if (recipe.getPrepTimeMinutes() != null) {
        sb.append("⏱️ <b>Tempo:</b> ").append(recipe.getPrepTimeMinutes()).append(" min");
    }
    if (recipe.getPortions() != null) {
        sb.append(" | 👥 <b>Rendimento:</b> ").append(recipe.getPortions()).append(" porções");
    }
    sb.append("\n\n");

    sb.append("🛒 <b>Ingredientes:</b>\n");
    if (recipe.getIngredients().isEmpty()) {
        sb.append("• Nenhum ingrediente especificado.\n");
    } else {
        for (Ingredient ing : recipe.getIngredients()) {
            sb.append("• ");
            if (ing.getQuantity() != null && !ing.getQuantity().isBlank()) {
                sb.append(TelegramHtmlUtil.escapeHtml(ing.getQuantity())).append(" ");
            }
            if (ing.getUnit() != null && !ing.getUnit().isBlank()) {
                sb.append(TelegramHtmlUtil.escapeHtml(ing.getUnit())).append(" de ");
            }
            sb.append(TelegramHtmlUtil.escapeHtml(ing.getName()));
            if (ing.getNotes() != null && !ing.getNotes().isBlank()) {
                sb.append(" (").append(TelegramHtmlUtil.escapeHtml(ing.getNotes())).append(")");
            }
            sb.append("\n");
        }
    }
    sb.append("\n");

    sb.append("👩‍🍳 <b>Modo de Preparo:</b>\n");
    if (recipe.getInstructions().isEmpty()) {
        sb.append("1. Nenhum passo especificado.\n");
    } else {
        for (Instruction inst : recipe.getInstructions()) {
            sb.append(inst.getStepNumber()).append(". ")
              .append(TelegramHtmlUtil.escapeHtml(inst.getInstructionText())).append("\n");
        }
    }

    return sb.toString();
}
```

### Passo 3: Atualizar `TelegramBotService.sendTextMessage`
Altere o `parseMode` para `"HTML"`:
```java
    private void sendTextMessage(Long chatId, String text) {
        try {
            SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("HTML")
                .build();
            telegramClient.execute(message);
        } catch (Exception e) {
            log.warn("Erro ao enviar mensagem HTML, tentando texto puro: {}", e.getMessage());
            try {
                SendMessage fallback = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text.replaceAll("<[^>]*>", ""))
                    .build();
                telegramClient.execute(fallback);
            } catch (Exception ex) {
                log.error("Erro fatal ao enviar mensagem Telegram para chatId {}: {}", chatId, ex.getMessage());
            }
        }
    }
```

### Passo 4: Tratar `message.hasAudio()` em `TelegramBotService.consume`
```java
    // 1. Mensagem de Voz (Microfone do Telegram)
    if (message.hasVoice()) {
        handleVoiceMessage(chatId, userName, message.getVoice().getFileId(), "audio/ogg");
    }
    // 2. Arquivo de Áudio (MP3 / Áudio salvo)
    else if (message.hasAudio()) {
        String mimeType = message.getAudio().getMimeType() != null ? message.getAudio().getMimeType() : "audio/mpeg";
        handleVoiceMessage(chatId, userName, message.getAudio().getFileId(), mimeType);
    }
    // 3. Mensagem de Texto
    else if (message.hasText()) {
        handleTextMessage(chatId, userName, message.getText().trim());
    }
```

### Passo 5: Criar Testes em `TelegramHtmlUtilTest.java`
Criar testes validando:
1. Escape correto de `<script>`, `&`, `<`, `>`.
2. Formatação de negrito e código.
3. Fallback de remoção de tags HTML.

---

## 🧪 Como Testar e Ter Certeza que Funciona
Execute:
```bash
./gradlew test --tests "ChefBot_Telegram.util.TelegramHtmlUtilTest"
```

### Critério de Sucesso:
- Testes passam com `BUILD SUCCESSFUL`.
- Nenhuma mensagem falha por erro de parsing de entidades no Telegram.
