package ChefBot_Telegram.service;

import ChefBot_Telegram.dto.RecipeResponseDTO;
import ChefBot_Telegram.model.Recipe;
import ChefBot_Telegram.util.TelegramHtmlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Component
public class TelegramBotService implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);

    private final String botToken;
    private final TelegramClient telegramClient;
    private final GeminiService geminiService;
    private final RecipeService recipeService;
    private final UserSessionService userSessionService;

    @Autowired
    public TelegramBotService(
            @Value("${telegram.bot.token}") String botToken,
            GeminiService geminiService,
            RecipeService recipeService,
            UserSessionService userSessionService) {
        this(botToken, new OkHttpTelegramClient(botToken), geminiService, recipeService, userSessionService);
    }

    TelegramBotService(
            String botToken,
            TelegramClient telegramClient,
            GeminiService geminiService,
            RecipeService recipeService,
            UserSessionService userSessionService) {
        this.botToken = botToken;
        this.telegramClient = telegramClient;
        this.geminiService = geminiService;
        this.recipeService = recipeService;
        this.userSessionService = userSessionService;
    }

    @Override
    public String getBotToken() {
        return this.botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage()) {
            return;
        }

        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String userName = message.getFrom() != null ? message.getFrom().getFirstName() : "Usuário";

        try {
            // 1. Tratamento de Mensagem de VOZ (Microfone do Telegram)
            if (message.hasVoice()) {
                handleAudioOrVoiceMessage(chatId, userName, message.getVoice().getFileId(), "audio/ogg");
            } 
            // 2. Tratamento de Mensagem de ÁUDIO (Arquivo MP3, áudio encaminhado, etc)
            else if (message.hasAudio()) {
                String mimeType = message.getAudio().getMimeType() != null ? message.getAudio().getMimeType() : "audio/mpeg";
                handleAudioOrVoiceMessage(chatId, userName, message.getAudio().getFileId(), mimeType);
            }
            // 3. Tratamento de Mensagem de TEXTO
            else if (message.hasText()) {
                handleTextMessage(chatId, userName, message.getText().trim());
            }
        } catch (Exception e) {
            log.error("Erro ao processar mensagem do Telegram para o chatId {}: {}", chatId, e.getMessage(), e);
            sendTextMessage(chatId, "⚠️ Desculpe, tive um problema ao processar sua mensagem. Tente novamente.");
        }
    }

    private void handleTextMessage(Long chatId, String userName, String text) {
        if (text.startsWith("/")) {
            String[] parts = text.split("\\s+", 2);
            String rawCmd = parts[0].toLowerCase();
            String cmd = rawCmd.contains("@") ? rawCmd.substring(0, rawCmd.indexOf('@')) : rawCmd;
            String args = parts.length > 1 ? parts[1].trim() : "";

            switch (cmd) {
                case "/start", "/ajuda", "/help" -> sendHelpMessage(chatId, userName);
                case "/receitas", "/minhasreceitas" -> listAllRecipes(chatId);
                case "/buscar", "/pesquisar" -> handleSearchCommand(chatId, args);
                case "/atualizar", "/editar" -> handleUpdateCommand(chatId, args);
                case "/deletar", "/apagar", "/excluir", "/remover" -> handleDeleteCommand(chatId, args);
                case "/cancelar", "/cancel" -> handleCancelCommand(chatId);
                default -> sendTextMessage(chatId, "❓ Comando não reconhecido. Digite <code>/ajuda</code> para ver os comandos disponíveis.");
            }
            return;
        }

        // Verifica se o usuário está em uma sessão de edição ativa
        Optional<UserSessionService.UserSession> activeSession = userSessionService.getSession(chatId);
        if (activeSession.isPresent() && activeSession.get().state() == UserSessionService.SessionState.AWAITING_RECIPE_UPDATE) {
            Long targetRecipeId = activeSession.get().targetRecipeId();
            userSessionService.clearSession(chatId);
            applyRecipeUpdate(chatId, targetRecipeId, text);
            return;
        }

        // Intercepta dúvidas ou pedidos de busca formulados em linguagem natural
        String lowerText = text.toLowerCase().trim();
        if (lowerText.startsWith("como faz ") || lowerText.startsWith("tem receita de ") || 
            lowerText.startsWith("qual a receita de ") || lowerText.startsWith("procurar ") ||
            lowerText.startsWith("busca ")) {
            
            String searchTerm = text.replaceAll("(?i)^(como faz|tem receita de|qual a receita de|procurar|busca)\\s*", "").trim();
            if (!searchTerm.isBlank()) {
                handleSearchCommand(chatId, searchTerm);
                return;
            }
        }

        // Se for texto comum, passa pelo Gemini para validação e extração
        sendTextMessage(chatId, "✍️ Analisando sua mensagem com IA...");

        try {
            RecipeResponseDTO dto = geminiService.extractRecipeFromText(text);

            if (dto.isRecipe() == null || !dto.isRecipe()) {
                sendTextMessage(chatId, "🧑‍🍳 Não identifiquei uma receita culinária na sua mensagem.\n\nEnvie uma receita em texto ou áudio (com ingredientes ou modo de preparo), ou use <code>/ajuda</code> para ver os comandos.");
                return;
            }

            Recipe savedRecipe = recipeService.saveRecipeFromDTO(chatId, userName, dto, text);

            String formattedResponse = "✅ <b>Receita salva com sucesso!</b>\n\n" + 
                                       recipeService.formatRecipeForTelegram(savedRecipe) +
                                       "\n💡 <b>Dica:</b> Para alterar algum dado no futuro, use <code>/atualizar " + savedRecipe.getId() + " &lt;o que mudar&gt;</code>";
            sendTextMessage(chatId, formattedResponse);
        } catch (Exception e) {
            log.error("Falha ao processar receita por texto: {}", e.getMessage(), e);
            sendTextMessage(chatId, "❌ Ocorreu um erro ao processar a receita. Tente novamente em instantes.");
        }
    }

    private void handleAudioOrVoiceMessage(Long chatId, String userName, String fileId, String mimeType) {
        sendTextMessage(chatId, "🎧 Ouvindo seu áudio e analisando com IA...");

        try {
            GetFile getFile = new GetFile(fileId);
            File file = telegramClient.execute(getFile);

            byte[] audioBytes;
            try (InputStream is = telegramClient.downloadFileAsStream(file)) {
                audioBytes = is.readAllBytes();
            }

            RecipeResponseDTO dto = geminiService.extractRecipeFromAudio(audioBytes, mimeType);

            if (dto.isRecipe() == null || !dto.isRecipe()) {
                sendTextMessage(chatId, "🧑‍🍳 Não identifiquei uma receita culinária no áudio.\n\nPor favor, grave ou envie um áudio explicando a receita (ingredientes ou modo de preparo).");
                return;
            }

            Recipe savedRecipe = recipeService.saveRecipeFromDTO(chatId, userName, dto, "[Áudio gravado]");

            String formattedResponse = "✅ <b>Áudio processado e receita salva!</b>\n\n" + 
                                       recipeService.formatRecipeForTelegram(savedRecipe) +
                                       "\n💡 <b>Dica:</b> Para alterar algum dado no futuro, use <code>/atualizar " + savedRecipe.getId() + " &lt;o que mudar&gt;</code>";
            sendTextMessage(chatId, formattedResponse);
        } catch (Exception e) {
            log.error("Falha ao processar áudio: {}", e.getMessage(), e);
            sendTextMessage(chatId, "❌ Ocorreu um erro ao ouvir o áudio. Verifique se a gravação está audível e tente novamente.");
        }
    }

    void handleSearchCommand(Long chatId, String args) {
        if (args.isBlank()) {
            sendTextMessage(chatId, """
                🔍 <b>Como buscar no seu caderno:</b>

                Você pode pesquisar de várias formas:
                • <b>Por Nome:</b> <code>/buscar bolo</code> ou <code>/buscar torta de limao</code>
                • <b>Por Ingrediente:</b> <code>/buscar chocolate</code> ou <code>/buscar frango</code>
                • <b>Por Categoria:</b> <code>/buscar sobremesa</code> ou <code>/buscar almoço</code>
                • <b>Por Modo de Preparo:</b> <code>/buscar airfryer</code> ou <code>/buscar forno</code>
                • <b>Por ID Direto:</b> <code>/buscar 1</code> (mostra a receita completa)

                💡 <b>Dica:</b> Não se preocupe com acentos ou letras maiúsculas!
                """);
            return;
        }

        List<Recipe> recipes = recipeService.searchRecipes(chatId, args);
        if (recipes.isEmpty()) {
            List<Recipe> recent = recipeService.findRecipesByChatId(chatId);
            StringBuilder sb = new StringBuilder();
            sb.append("🔍 <b>Nenhuma receita encontrada para:</b> \"").append(TelegramHtmlUtil.escapeHtml(args)).append("\"\n\n");
            sb.append("💡 <b>Dicas para encontrar:</b>\n");
            sb.append("• Tente usar uma única palavra-chave (ex: 'frango' em vez de 'frango desfiado com molho').\n");
            sb.append("• Use <code>/receitas</code> para ver todas as suas receitas cadastradas.\n");

            if (!recent.isEmpty()) {
                sb.append("\n📚 <b>Suas receitas recentes:</b>\n");
                int limit = Math.min(3, recent.size());
                for (int i = 0; i < limit; i++) {
                    sb.append(recipeService.formatRecipeSummary(recent.get(i))).append("\n");
                }
            }
            sendTextMessage(chatId, sb.toString());
            return;
        }

        // Se houver exatamente 1 resultado, exibe a ficha completa
        if (recipes.size() == 1) {
            sendTextMessage(chatId, recipeService.formatRecipeForTelegram(recipes.get(0)));
            return;
        }

        // Se houver múltiplos resultados, exibe uma lista compacta em UMA única mensagem
        StringBuilder sb = new StringBuilder();
        sb.append("🔍 <b>Encontrei ").append(recipes.size()).append(" receitas para \"").append(TelegramHtmlUtil.escapeHtml(args)).append("\":</b>\n\n");

        for (Recipe r : recipes) {
            sb.append(recipeService.formatRecipeSummary(r)).append("\n");
        }

        sb.append("\n👉 <b>Para abrir a receita completa, digite:</b> <code>/buscar &lt;ID&gt;</code> (ex: <code>/buscar ").append(recipes.get(0).getId()).append("</code>)");
        sendTextMessage(chatId, sb.toString());
    }

    private void handleCancelCommand(Long chatId) {
        if (userSessionService.getSession(chatId).isPresent()) {
            userSessionService.clearSession(chatId);
            sendTextMessage(chatId, "❌ Edição cancelada.");
        } else {
            sendTextMessage(chatId, "ℹ️ Nenhuma operação pendente para cancelar.");
        }
    }

    void handleUpdateCommand(Long chatId, String args) {
        if (args.isBlank()) {
            sendTextMessage(chatId, """
                ✏️ <b>Como atualizar uma receita:</b>

                Você pode atualizar de duas formas fáceis:
                • <b>Em 2 passos:</b> Digite <code>/atualizar &lt;ID&gt;</code> e depois envie as alterações na próxima mensagem.
                • <b>Em 1 passo:</b> Digite <code>/atualizar &lt;ID&gt; &lt;alteração&gt;</code>
                • <b>Por Nome:</b> Digite <code>/atualizar bolo trocar açúcar por adoçante</code>

                <b>Exemplos de uso:</b>
                • <code>/atualizar 1</code> (entra no modo interativo)
                • <code>/atualizar 1 Mudar o autor para Minha Mãe</code>
                • <code>/atualizar 1 O tempo de preparo é de 40 minutos</code>

                💡 Use <code>/receitas</code> para consultar o número de ID das suas receitas salvas.
                """);
            return;
        }

        String[] parts = args.split("\\s+", 2);
        String firstToken = parts[0].replace("#", "").trim();
        String instructions = parts.length > 1 ? parts[1].trim() : "";

        Long recipeId = null;
        if (firstToken.matches("^\\d+$")) {
            recipeId = Long.parseLong(firstToken);
        } else {
            // Tentar localizar a receita pelo nome
            List<Recipe> matches = recipeService.searchRecipes(chatId, firstToken);
            if (matches.isEmpty()) {
                sendTextMessage(chatId, "❌ Nenhuma receita encontrada com o termo \"" + TelegramHtmlUtil.escapeHtml(firstToken) + "\". Use <code>/receitas</code> para consultar seus IDs.");
                return;
            } else if (matches.size() == 1) {
                recipeId = matches.get(0).getId();
            } else {
                StringBuilder sb = new StringBuilder("🔍 Encontrei mais de uma receita. Por favor, especifique o ID correspondente:\n\n");
                for (Recipe r : matches) {
                    sb.append(recipeService.formatRecipeSummary(r)).append("\n");
                }
                sb.append("\nExemplo: <code>/atualizar ").append(matches.get(0).getId()).append(" ").append(TelegramHtmlUtil.escapeHtml(instructions)).append("</code>");
                sendTextMessage(chatId, sb.toString());
                return;
            }
        }

        Optional<Recipe> optionalRecipe = recipeService.findRecipeByUserAndId(chatId, recipeId);
        if (optionalRecipe.isEmpty()) {
            sendTextMessage(chatId, "❌ Receita com ID #" + recipeId + " não foi encontrada no seu caderno de receitas.");
            return;
        }

        Recipe existingRecipe = optionalRecipe.get();

        if (instructions.isBlank()) {
            userSessionService.startUpdateSession(chatId, recipeId);
            sendTextMessage(chatId, "✏️ <b>Modo de edição ativado para:</b> <b>" + TelegramHtmlUtil.escapeHtml(existingRecipe.getTitle()) + "</b> <code>(#" + recipeId + ")</code>\n\n" +
                "Envie agora uma mensagem dizendo o que deseja alterar (ex: 'mudar o autor para Vovó' ou 'trocar açúcar por adoçante').\n\n" +
                "💡 Para cancelar a qualquer momento, digite <code>/cancelar</code>.");
            return;
        }

        applyRecipeUpdate(chatId, recipeId, instructions);
    }

    void applyRecipeUpdate(Long chatId, Long recipeId, String instructions) {
        Optional<Recipe> optionalRecipe = recipeService.findRecipeByUserAndId(chatId, recipeId);
        if (optionalRecipe.isEmpty()) {
            sendTextMessage(chatId, "❌ Receita com ID #" + recipeId + " não foi encontrada no seu caderno.");
            return;
        }

        Recipe existingRecipe = optionalRecipe.get();
        sendTextMessage(chatId, "🔄 Atualizando a receita <b>" + TelegramHtmlUtil.escapeHtml(existingRecipe.getTitle()) + "</b>...");

        try {
            RecipeResponseDTO updatedDTO = geminiService.updateRecipe(existingRecipe, instructions);
            Recipe updatedRecipe = recipeService.updateRecipeFromDTO(chatId, recipeId, updatedDTO);

            String responseMsg = "✅ <b>Receita atualizada com sucesso!</b>\n\n" + 
                                 recipeService.formatRecipeForTelegram(updatedRecipe);
            sendTextMessage(chatId, responseMsg);
        } catch (Exception e) {
            log.error("Erro ao atualizar receita #{}: {}", recipeId, e.getMessage(), e);
            sendTextMessage(chatId, "❌ Ocorreu um erro ao atualizar a receita. Tente novamente com mais detalhes.");
        }
    }

    private void handleDeleteCommand(Long chatId, String args) {
        if (args.isBlank()) {
            sendTextMessage(chatId, """
                🗑️ <b>Como deletar uma receita:</b>

                Digite: <code>/deletar &lt;ID&gt;</code> informando o número da receita.

                <b>Exemplo:</b>
                • <code>/deletar 1</code>

                💡 Use <code>/receitas</code> para consultar os IDs das suas receitas.
                """);
            return;
        }

        String idStr = args.replace("#", "").trim();
        Long recipeId;
        try {
            recipeId = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            sendTextMessage(chatId, "❌ ID inválido. Use um número (ex: <code>/deletar 1</code>).");
            return;
        }

        Optional<Recipe> optionalRecipe = recipeService.findRecipeByUserAndId(chatId, recipeId);
        if (optionalRecipe.isEmpty()) {
            sendTextMessage(chatId, "❌ Receita com ID #" + recipeId + " não foi encontrada no seu caderno de receitas.");
            return;
        }

        String title = optionalRecipe.get().getTitle();
        boolean deleted = recipeService.deleteRecipe(chatId, recipeId);

        if (deleted) {
            sendTextMessage(chatId, "🗑️ <b>Receita <code>[#" + recipeId + "] " + TelegramHtmlUtil.escapeHtml(title) + "</code> foi excluída com sucesso!</b>");
        } else {
            sendTextMessage(chatId, "❌ Não foi possível excluir a receita. Tente novamente.");
        }
    }

    private void listAllRecipes(Long chatId) {
        List<Recipe> recipes = recipeService.findRecipesByChatId(chatId);
        if (recipes.isEmpty()) {
            sendTextMessage(chatId, "📖 Você ainda não tem nenhuma receita salva.\n\nEnvie um áudio ou texto ensinando uma receita para começar!");
            return;
        }

        StringBuilder sb = new StringBuilder("📚 <b>Suas Receitas Salvas:</b>\n\n");
        for (Recipe r : recipes) {
            sb.append("• <code>[#").append(r.getId()).append("]</code> <b>").append(TelegramHtmlUtil.escapeHtml(r.getTitle())).append("</b>");
            if (r.getSourceAuthor() != null && !r.getSourceAuthor().isBlank()) {
                sb.append(" <i>(por ").append(TelegramHtmlUtil.escapeHtml(r.getSourceAuthor())).append(")</i>");
            }
            sb.append("\n");
        }
        sb.append("\n💡 <b>Dicas:</b>\n• Digite <code>/buscar &lt;nome ou ID&gt;</code> para ver os detalhes da receita.\n• Digite <code>/atualizar &lt;ID&gt; &lt;alteração&gt;</code> para editar qualquer informação.\n• Digite <code>/deletar &lt;ID&gt;</code> para apagar uma receita.");
        sendTextMessage(chatId, sb.toString());
    }

    private void sendHelpMessage(Long chatId, String userName) {
        String msg = String.format("""
            👨‍🍳 <b>Olá, %s! Bem-vindo ao ChefBot!</b>

            Eu sou o seu caderno inteligente de receitas.
            
            <b>Como salvar novas receitas:</b>
            🎙️ <b>Áudio:</b> Mande um áudio explicando uma receita com ingredientes e preparo.
            ✍️ <b>Texto:</b> Envie o texto da receita de forma livre.
            
            <b>Comandos disponíveis:</b>
            • <code>/receitas</code> - Lista todas as suas receitas salvas com seus IDs.
            • <code>/buscar &lt;termo&gt;</code> - Busca receitas por nome, ingrediente, categoria ou ID.
            • <code>/atualizar &lt;ID&gt; &lt;alteração&gt;</code> - Atualiza título, autor, ingredientes ou preparo de uma receita existente.
            • <code>/deletar &lt;ID&gt;</code> - Exclui uma receita do seu caderno.
            • <code>/ajuda</code> - Mostra esta mensagem de ajuda.
            """, TelegramHtmlUtil.escapeHtml(userName));
        sendTextMessage(chatId, msg);
    }

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
                    .text(TelegramHtmlUtil.stripHtml(text))
                    .build();
                telegramClient.execute(fallback);
            } catch (Exception ex) {
                log.error("Erro fatal ao enviar mensagem Telegram para chatId {}: {}", chatId, ex.getMessage());
            }
        }
    }
}


